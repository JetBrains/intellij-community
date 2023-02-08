// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMapper
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.imports.canBeReferencedViaImport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Tested with TextJavaToKotlinCopyPasteConversionTestGenerated
 */
class PlainTextPasteImportResolver(private val dataForConversion: DataForConversion, val targetFile: KtFile) {
    private val file = dataForConversion.file
    private val project = targetFile.project

    private val importList = file.importList!!

    // keep access to deprecated PsiElementFactory.SERVICE for bwc with <= 191
    private val psiElementFactory = PsiElementFactory.getInstance(project)

    private val bindingContext by lazy { targetFile.analyzeWithContent() }
    private val resolutionFacade = targetFile.getResolutionFacade()

    private val shortNameCache = PsiShortNamesCache.getInstance(project)
    private val scope = file.resolveScope

    private val failedToResolveReferenceNames = HashSet<String>()
    private var ambiguityInResolution = false
    private var couldNotResolve = false

    val addedImports = mutableListOf<PsiImportStatementBase>()

    private fun canBeImported(descriptor: DeclarationDescriptorWithVisibility?): Boolean {
        return descriptor != null
                && descriptor.canBeReferencedViaImport()
                && descriptor.isVisible(targetFile, null, bindingContext, resolutionFacade)
    }

    private fun addImport(importStatement: PsiImportStatementBase, shouldAddToTarget: Boolean = false) {
        file.importList?.let {
            it.add(importStatement)
            if (shouldAddToTarget)
                addedImports.add(importStatement)
        }
    }

    fun addImportsFromTargetFile() {
        if (importList in dataForConversion.elementsAndTexts.toList()) return

        val task = {
            val addImportList = mutableListOf<PsiImportStatementBase>()

            fun tryConvertKotlinImport(importDirective: KtImportDirective) {
                val importPath = importDirective.importPath
                val importedReference = importDirective.importedReference
                if (importPath != null && !importPath.hasAlias() && importedReference is KtDotQualifiedExpression) {
                    val receiver = importedReference
                        .receiverExpression
                        .referenceExpression()
                        ?.mainReference
                        ?.resolve()
                    val selector = importedReference
                        .selectorExpression
                        ?.referenceExpression()
                        ?.mainReference
                        ?.resolve()

                    val isPackageReceiver = receiver is PsiPackage
                    val isClassReceiver = receiver is PsiClass
                    val isClassSelector = selector is PsiClass

                    if (importPath.isAllUnder) {
                        when {
                            isClassReceiver ->
                                addImportList.add(psiElementFactory.createImportStaticStatement(receiver as PsiClass, "*"))
                            isPackageReceiver ->
                                addImportList.add(psiElementFactory.createImportStatementOnDemand((receiver as PsiPackage).qualifiedName))
                        }
                    } else {
                        when {
                            isClassSelector ->
                                addImportList.add(psiElementFactory.createImportStatement(selector as PsiClass))
                            isClassReceiver ->
                                addImportList.add(
                                    psiElementFactory.createImportStaticStatement(
                                        receiver as PsiClass,
                                        importPath.importedName!!.asString()
                                    )
                                )
                        }
                    }
                }
            }

            runReadAction {
                val importDirectives = targetFile.importDirectives
                importDirectives.forEachIndexed { index, value ->
                    ProgressManager.checkCanceled()
                    ProgressManager.getInstance().progressIndicator?.fraction = 1.0 * index / importDirectives.size
                    tryConvertKotlinImport(value)
                }
            }

            ApplicationManager.getApplication().invokeAndWait {
                runWriteAction { addImportList.forEach { addImport(it) } }
            }
        }

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            task, KotlinBundle.message("copy.text.adding.imports"), true, project
        )
    }

    fun tryResolveReferences() {
        val task = {
            fun performWriteAction(block: () -> Unit) {
                ApplicationManager.getApplication().invokeAndWait { runWriteAction { block() } }
            }

            fun tryResolveReference(reference: PsiQualifiedReference): Boolean {
                ProgressManager.checkCanceled()
                if (runReadAction { reference.resolve() } != null) return true
                val referenceName = runReadAction { reference.referenceName } ?: return false
                if (referenceName in failedToResolveReferenceNames) return false
                if (runReadAction { reference.qualifier } != null) return false

                val classes = runReadAction {
                    shortNameCache.getClassesByName(referenceName, scope)
                        .mapNotNull { psiClass ->
                            val containingFile = psiClass.containingFile
                            if (RootKindFilter.everything.matches(containingFile)) {
                                psiClass to psiClass.getJavaMemberDescriptor() as? ClassDescriptor
                            } else null
                        }.filter { canBeImported(it.second) }
                }

                classes.find { (_, descriptor) ->
                    JavaToKotlinClassMapper.mapPlatformClass(descriptor!!).isNotEmpty()
                }?.let { (psiClass, _) ->
                    performWriteAction { addImport(psiElementFactory.createImportStatement(psiClass)) }
                }
                if (runReadAction { reference.resolve() } != null) return true

                classes.singleOrNull()?.let { (psiClass, _) ->
                    performWriteAction { addImport(psiElementFactory.createImportStatement(psiClass), true) }
                }

                when {
                    runReadAction { reference.resolve() } != null -> return true
                    classes.isNotEmpty() -> {
                        ambiguityInResolution = true
                        return false
                    }
                }

                val members = runReadAction {
                    (shortNameCache.getMethodsByName(referenceName, scope).asList() +
                            shortNameCache.getFieldsByName(referenceName, scope).asList())
                        .asSequence()
                        .map { it as PsiMember }
                        .filter { it.moduleInfoOrNull != null }
                        .map { it to it.getJavaMemberDescriptor(resolutionFacade) as? DeclarationDescriptorWithVisibility }
                        .filter { canBeImported(it.second) }
                        .toList()
                }
                ProgressManager.checkCanceled()
                members.singleOrNull()?.let { (psiMember, _) ->
                    performWriteAction {
                        addImport(
                            psiElementFactory.createImportStaticStatement(psiMember.containingClass!!, psiMember.name!!),
                            true
                        )
                    }
                }

                when {
                    runReadAction { reference.resolve() } != null -> return false
                    members.isNotEmpty() -> ambiguityInResolution = true
                    else -> couldNotResolve = true
                }
                return false
            }


            val smartPointerManager = SmartPointerManager.getInstance(file.project)
            val elementsWithUnresolvedRef = project.runReadActionInSmartMode {
                PsiTreeUtil.collectElements(file) { element ->
                    element.reference != null
                            && element.reference is PsiQualifiedReference
                            && element.reference?.resolve() == null
                }.map { smartPointerManager.createSmartPsiElementPointer(it) }
            }

            val reversed = elementsWithUnresolvedRef.reversed()
            val progressIndicator = ProgressManager.getInstance().progressIndicator
            progressIndicator?.isIndeterminate = false
            reversed.forEachIndexed { index, value ->
                progressIndicator?.fraction = 1.0 * index / reversed.size
                runReadAction { value.element?.reference?.safeAs<PsiQualifiedReference>() }?.let { reference ->
                    if (!tryResolveReference(reference)) {
                        runReadAction { reference.referenceName }?.let {
                            failedToResolveReferenceNames += it
                        }
                    }
                }
            }
        }

        ProgressManager.checkCanceled()
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            task, KotlinBundle.message("copy.text.resolving.references"), true, project
        )

    }
}
