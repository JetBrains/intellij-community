// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.shorten

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.KotlinBaseFe10CodeInsightBundle
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.ShortenReferences.Options
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.util.*

interface DelayedRefactoringRequest

class ShorteningRequest(val pointer: SmartPsiElementPointer<KtElement>, val options: Options) : DelayedRefactoringRequest
class ImportRequest(
    val elementToImportPointer: SmartPsiElementPointer<PsiElement>,
    val filePointer: SmartPsiElementPointer<KtFile>
) : DelayedRefactoringRequest

private var Project.delayedRefactoringRequests: MutableSet<DelayedRefactoringRequest>?
        by UserDataProperty(Key.create("DELAYED_REFACTORING_REQUESTS"))

/*
 * When one refactoring invokes another this value must be set to false so that shortening wait-set is not cleared
 * and previously collected references are processed correctly. Afterwards it must be reset to original value
 */
var Project.ensureNoRefactoringRequestsBeforeRefactoring: Boolean
        by NotNullableUserDataProperty(Key.create("ENSURE_NO_REFACTORING_REQUESTS_BEFORE_REFACTORING"), true)

fun Project.runRefactoringAndKeepDelayedRequests(action: () -> Unit) {
    val ensureNoRefactoringRequests = ensureNoRefactoringRequestsBeforeRefactoring

    try {
        ensureNoRefactoringRequestsBeforeRefactoring = false
        action()
    } finally {
        ensureNoRefactoringRequestsBeforeRefactoring = ensureNoRefactoringRequests
    }
}

// We need this function so that we can modify the options of the ShorteningRequest before shortening.
// For example, it is used to enable fully shortening references to the same as their original references when copying and pasting.
@ApiStatus.Internal
fun Project.modifyExistingShorteningRequests(action: (ShorteningRequest) -> ShorteningRequest) {
    val newRequests = delayedRefactoringRequests?.mapTo(mutableSetOf()) {
        if (it is ShorteningRequest) {
            action(it)
        } else it
    }
    delayedRefactoringRequests = newRequests
}

private fun Project.getOrCreateRefactoringRequests(): MutableSet<DelayedRefactoringRequest> {
    var requests = delayedRefactoringRequests
    if (requests == null) {
        requests = LinkedHashSet()
        delayedRefactoringRequests = requests
    }

    return requests
}

fun KtElement.addToShorteningWaitSet(options: Options = Options.DEFAULT) {
    assert(ApplicationManager.getApplication()!!.isWriteAccessAllowed) { "Write access needed" }
    val project = project
    val elementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)
    project.getOrCreateRefactoringRequests().add(ShorteningRequest(elementPointer, options))
}

fun addDelayedImportRequest(elementToImport: PsiElement, file: KtFile) {
    assert(ApplicationManager.getApplication()!!.isWriteAccessAllowed) { "Write access needed" }
    file.project.getOrCreateRefactoringRequests() += ImportRequest(elementToImport.createSmartPointer(), file.createSmartPointer())
}

fun performDelayedRefactoringRequests(project: Project, defaultOptions: Options = Options.DEFAULT) {
    project.delayedRefactoringRequests?.let { requests ->
        project.delayedRefactoringRequests = null
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val shorteningRequests = ArrayList<ShorteningRequest>()
        val importRequests = ArrayList<ImportRequest>()
        requests.forEach {
            when (it) {
                is ShorteningRequest -> shorteningRequests += it
                is ImportRequest -> importRequests += it
            }
        }

        val elementToOptions = shorteningRequests.mapNotNull { req -> req.pointer.element?.let { it to req.options } }.toMap()
        val elements = elementToOptions.keys
        //TODO: this is not correct because it should not shorten deep into the elements!
        ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
            KotlinBaseFe10CodeInsightBundle.message("progress.title.shortening.references"),
            project,
            null
        ) {
            ShortenReferences { elementToOptions[it] ?: defaultOptions }.process(elements)
        }

        val importInsertHelper = ImportInsertHelper.getInstance(project)

        for ((file, requestsForFile) in importRequests.groupBy { it.filePointer.element }) {
            if (file == null) continue

            for (requestForFile in requestsForFile) {
                val elementToImport = requestForFile.elementToImportPointer.element?.unwrapped ?: continue
                val descriptorToImport = when (elementToImport) {
                    is KtDeclaration -> elementToImport.unsafeResolveToDescriptor(BodyResolveMode.PARTIAL)
                    is PsiMember -> elementToImport.getJavaMemberDescriptor()
                    else -> null
                } ?: continue
                importInsertHelper.importDescriptor(file, descriptorToImport)
            }
        }
    }
}

private val LOG = Logger.getInstance(Project::class.java.canonicalName)

fun prepareDelayedRequests(project: Project) {
    val requests = project.delayedRefactoringRequests
    if (project.ensureNoRefactoringRequestsBeforeRefactoring && !requests.isNullOrEmpty()) {
        LOG.warn("Waiting set for reference shortening is not empty")
        project.delayedRefactoringRequests = null
    }
}

var KtElement.isToBeShortened: Boolean? by CopyablePsiUserDataProperty(Key.create("IS_TO_BE_SHORTENED"))

fun KtElement.addToBeShortenedDescendantsToWaitingSet() {
    forEachDescendantOfType<KtElement> {
        if (it.isToBeShortened == true) {
            it.isToBeShortened = null
            it.addToShorteningWaitSet()
        }
    }
}