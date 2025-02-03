// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.core.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.refactoring.broadcastRefactoringExit
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

class KotlinChangeSignatureProcessor(
    project: Project,
    changeInfo: KotlinChangeInfo,
    @NlsContexts.Command private val commandName: String
) : ChangeSignatureProcessorBase(project, changeInfo) {
    init {
        // we must force collecting references to other parameters now before the signature is changed
        changeInfo.newParameters.forEach { it.defaultValueParameterReferences }
    }

    override fun setPrepareSuccessfulSwingThreadCallback(callback: Runnable?) {
        val actualCallback = if (callback != null) {
            Runnable {
                callback.run()
                setPrepareSuccessfulSwingThreadCallback(null)
            }
        } else null
        super.setPrepareSuccessfulSwingThreadCallback(actualCallback)
    }

    override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
        val subject = if (changeInfo.kind.isConstructor)
            KotlinBundle.message("text.constructor")
        else
            KotlinBundle.message("text.function")

        return KotlinUsagesViewDescriptor(myChangeInfo.method, RefactoringBundle.message("0.to.change.signature", subject))
    }

    override fun getChangeInfo(): KotlinChangeInfo = super.getChangeInfo() as KotlinChangeInfo

    protected override fun findUsages(): Array<UsageInfo> {
        val allUsages = ArrayList<UsageInfo>()
        val javaUsages = mutableSetOf<UsageInfo>()
        changeInfo.getOrCreateJavaChangeInfos()?.let { javaChangeInfos ->
            val javaProcessor = JavaChangeSignatureUsageProcessor()
            javaChangeInfos.mapNotNullTo(allUsages) { javaChangeInfo ->
                val javaUsagesForKtChange = javaProcessor.findUsages(javaChangeInfo)
                val uniqueJavaUsagesForKtChange = javaUsagesForKtChange.filter<UsageInfo> {
                    it.element?.language != KotlinLanguage.INSTANCE && !javaUsages.contains(it)
                }.ifEmpty { return@mapNotNullTo null }

                javaUsages.addAll(uniqueJavaUsagesForKtChange)
                KotlinWrapperForJavaUsageInfos(changeInfo, javaChangeInfo, uniqueJavaUsagesForKtChange.toTypedArray(), changeInfo.method)
            }
        }

        val primaryConstructor = changeInfo.method as? KtPrimaryConstructor
        if (primaryConstructor != null) {
            findConstructorPropertyUsages(primaryConstructor, allUsages)
        }

        super.findUsages().filterTo(allUsages) { it is KotlinUsageInfo<*> || it is UnresolvableCollisionUsageInfo }
        return allUsages.toTypedArray()
    }

    private fun findConstructorPropertyUsages(
        primaryConstructor: KtPrimaryConstructor,
        allUsages: ArrayList<UsageInfo>
    ) {
        for ((index, parameter) in primaryConstructor.valueParameters.withIndex()) {
            if (!parameter.isOverridable) continue

            val parameterInfo = changeInfo.newParameters.find { it.originalIndex == index } ?: continue
            val descriptor = parameter.resolveToDescriptorIfAny() as? PropertyDescriptor ?: continue
            val methodDescriptor = KotlinChangeSignatureData(
                descriptor,
                parameter,
                listOf(descriptor),
            )

            val propertyChangeInfo = KotlinChangeInfo(
                methodDescriptor,
                name = parameterInfo.name,
                newReturnTypeInfo = parameterInfo.currentTypeInfo,
                context = parameter,
            )

            changeInfo.registerInnerChangeInfo(propertyChangeInfo)
            KotlinChangeSignatureProcessor(myProject, propertyChangeInfo, commandName).findUsages().mapNotNullTo(allUsages) {
                if (it is KotlinWrapperForJavaUsageInfos) return@mapNotNullTo it

                val element = it.element
                if (element != null && !(it is KotlinCallableDefinitionUsage<*> && element == parameter))
                    KotlinWrapperForPropertyInheritorsUsage(propertyChangeInfo, it, element)
                else
                    null
            }
        }
    }

    protected override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usageProcessors = ChangeSignatureUsageProcessor.EP_NAME.extensions

        if (!usageProcessors.all { it.setupDefaultValues(myChangeInfo, refUsages, myProject) }) return false

        val conflictDescriptions = object : MultiMap<PsiElement, String>() {
            override fun createCollection() = LinkedHashSet<String>()
        }

        usageProcessors.forEach { conflictDescriptions.putAllValues(it.findConflicts(myChangeInfo, refUsages)) }

        val usages = refUsages.get()
        val usagesSet = usages.toHashSet()

        RenameUtil.addConflictDescriptions(usages, conflictDescriptions)
        RenameUtil.removeConflictUsages(usagesSet)

        val usageArray = usagesSet.sortedWith(Comparator { u1, u2 ->
            if (u1 is KotlinImplicitReceiverUsage && u2 is KotlinFunctionCallUsage) return@Comparator -1
            if (u2 is KotlinImplicitReceiverUsage && u1 is KotlinFunctionCallUsage) return@Comparator 1
            val element1 = u1.element
            val element2 = u2.element
            val rank1 = element1?.textOffset ?: -1
            val rank2 = element2?.textOffset ?: -1
            rank2 - rank1 // Reverse order
        }).toTypedArray()

        refUsages.set(usageArray)
        return showConflicts(conflictDescriptions, usageArray)
    }

    protected override fun isPreviewUsages(usages: Array<UsageInfo>): Boolean = isPreviewUsages

    override fun getCommandName() = commandName

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        super.performRefactoring(usages)
        usages.forEach {
            val callExpression = it.element as? KtCallExpression ?: return@forEach
            if (callExpression.canMoveLambdaOutsideParentheses()) {
                callExpression.moveFunctionLiteralOutsideParentheses()
            }
        }
        performDelayedRefactoringRequests(myProject)
    }

    override fun doRun() = try {
        super.doRun()
    } finally {
        broadcastRefactoringExit(myProject, refactoringId!!)
    }
}
