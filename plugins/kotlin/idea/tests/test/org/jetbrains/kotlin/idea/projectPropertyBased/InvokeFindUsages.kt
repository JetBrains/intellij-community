// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectPropertyBased

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.propertyBased.ActionOnFile
import com.intellij.testFramework.propertyBased.MadTestingUtil
import com.intellij.usages.Usage
import com.intellij.util.Processors
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.kotlin.idea.findUsages.KotlinClassFindUsagesOptions
import org.jetbrains.kotlin.idea.findUsages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.findUsages.KotlinPropertyFindUsagesOptions
import org.jetbrains.kotlin.idea.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.util.*

class InvokeFindUsages(file: PsiFile): ActionOnFile(file) {
    override fun performCommand(env: ImperativeCommand.Environment) {
        // ignore test data, and other resources
        file.takeIf { it.isUnderKotlinSourceRootTypes() } ?: return

        val project = project
        val editor =
            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile, 0), true)
                ?: error("Unable to open file $virtualFile")

        // todo: it's suboptimal search
        val (offset: Int, element: PsiElement) = run {
            val attempts = env.generateValue(Generator.integers(10, 30), null)
            for (attempt in 0 until attempts) {
                val offset = generateDocOffset(env, null)

                val element = when (GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)) {
                    null -> GotoDeclarationAction.findTargetElement(project, editor, offset)
                    else -> GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)
                } ?: continue
                if (element is KtElement) {
                    return@run (offset to element)
                }
            }
            env.logMessage("Unable to look up element for find usage in $attempts attempts")
            return
        }

        env.logMessage("Go to ${MadTestingUtil.getPositionDescription(offset, document)}")

        env.logMessage("Command find usages is called on element '$element' of ${element.javaClass.name}")
        val findUsagesManager = FindManager.getInstance(project).cast<FindManagerImpl>().findUsagesManager
        val handler = findUsagesManager.getFindUsagesHandler(
            element, FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS
        ) ?: run {
            env.logMessage("No find usage handler found for the element: '${element.text}'")
            return
        }

        val findUsagesOptions = when (element) {
            is KtFunction -> KotlinFunctionFindUsagesOptions(project).apply {
                // TODO: randomize isOverridingMethods etc
                isOverridingMethods = false
                isImplementingMethods = false
                isCheckDeepInheritance = true
                isIncludeInherited = false
                isIncludeOverloadUsages = false
                isImplicitToString = true
                isSearchForBaseMethod = true
                isSkipImportStatements = false
                isSearchForTextOccurrences = false
                isUsages = true
                searchExpected = true
            }
            is KtClassOrObject -> KotlinClassFindUsagesOptions(project).apply {
                searchExpected = true
                searchConstructorUsages = true
                isMethodsUsages = false
                isFieldsUsages = false
                isDerivedClasses = false
                isImplementingClasses = false
                isDerivedInterfaces = false
                isCheckDeepInheritance = true
                isIncludeInherited = false
                isSkipImportStatements = false
                isSearchForTextOccurrences = true
                isUsages = true
            }
            else -> KotlinPropertyFindUsagesOptions(project).apply {
                searchExpected = true
                isReadWriteAccess = true
                searchOverrides = false
                isReadAccess = true
                isWriteAccess = true
                isSearchForAccessors = false
                isSearchInOverridingMethods = false
                isSearchForBaseAccessors = false
                isSkipImportStatements = false
                isSearchForTextOccurrences = false
                isUsages = true
            }
        }
        val usages = mutableListOf<Usage>()
        val processor = Processors.cancelableCollectProcessor(Collections.synchronizedList(usages))

        val usageSearcher =
            FindUsagesManager.createUsageSearcher(handler, handler.primaryElements, handler.secondaryElements, findUsagesOptions)

        val task = object : Backgroundable(project, FindBundle.message("progress.title.finding.usages")) {
            override fun run(indicator: ProgressIndicator) {
                usageSearcher.generate(processor)
            }
        }
        ProgressManager.getInstance().cast<ProgressManagerImpl>()
            .runProcessWithProgressInCurrentThread(task, EmptyProgressIndicator(), ModalityState.defaultModalityState())

        env.logMessage("Found ${usages.size} usages for element $element")
    }
}