// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.KotlinCrossLanguageQuickFixAction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeParameter
import java.lang.ref.WeakReference

abstract class CreateCallableFromUsageFixBase<E : KtElement>(
    originalExpression: E,
    val isExtension: Boolean
) : KotlinCrossLanguageQuickFixAction<E>(originalExpression) {

    private var callableInfoReference: WeakReference<List<CallableInfo>>? = null

    protected open val callableInfos: List<CallableInfo>
        get() = listOfNotNull(callableInfo)

    protected open val callableInfo: CallableInfo?
        get() = throw UnsupportedOperationException()

    private fun callableInfos(): List<CallableInfo> =
        callableInfoReference?.get() ?: callableInfos.also {
            callableInfoReference = WeakReference(it)
        }

    protected fun notEmptyCallableInfos() = callableInfos().takeIf { it.isNotEmpty() }

    private var initialized: Boolean = false

    private fun StringBuilder.renderCallableKindsAndNamesAsString(renderedCallablesByKind: Map<CallableKind, List<String>>) =
        renderedCallablesByKind.entries.joinTo(this) {
            val kind = it.key
            val names = it.value.filter { name -> name.isNotEmpty() }
            val pluralIndex = if (names.size > 1) 2 else 1
            val kindText = when (kind) {
                CallableKind.FUNCTION -> KotlinBundle.message("text.function.0", pluralIndex)
                CallableKind.CONSTRUCTOR -> KotlinBundle.message("text.secondary.constructor")
                CallableKind.PROPERTY -> KotlinBundle.message("text.property.0", pluralIndex)
                else -> throw AssertionError("Unexpected callable info: $it")
            }
            if (names.isEmpty()) kindText else "$kindText ${names.joinToString { name -> "'$name'" }}"
        }

    /**
     * Builds a message to inform the creation of callables.
     */
    private fun buildCallableCreationMessage(hasReceiverType: Boolean, renderedCallablesByKind: Map<CallableKind, List<String>>) =
        buildString {
            append(KotlinBundle.message("text.create"))
            append(' ')

            val isAbstract = callableInfos.any { it.isAbstract }
            if (isAbstract) {
                append(KotlinBundle.message("text.abstract"))
                append(' ')
            } else if (isExtension) {
                append(KotlinBundle.message("text.extension"))
                append(' ')
            } else if (hasReceiverType) {
                append(KotlinBundle.message("text.member"))
                append(' ')
            }

            renderCallableKindsAndNamesAsString(renderedCallablesByKind)
        }

    open fun CallableInfo.renderReceiver(element: E, baseCallableReceiverTypeInfo: TypeInfoBase) = ""

    /**
     * TODO: Figure out whether we really need [baseCallableReceiverTypeInfo] or not.
     */
    private fun CallableInfo.renderCallable(baseCallableReceiverTypeInfo: TypeInfoBase): String = buildString {
        val element = element ?: return ""
        val callableInfo = this@renderCallable
        if (callableInfo.name.isEmpty()) return ""
        append(callableInfo.renderReceiver(element, baseCallableReceiverTypeInfo))
        append(callableInfo.name)
    }

    protected open val calculatedText: String by lazy(fun(): String {
        val callableInfos = notEmptyCallableInfos() ?: return ""
        val baseCallableInfo = callableInfos.first()
        val receiverTypeInfoOfBaseCallable = baseCallableInfo.receiverTypeInfo
        val renderedCallablesByKind = callableInfos.groupBy({ it.kind }, valueTransform = {
            it.renderCallable(receiverTypeInfoOfBaseCallable)
        })

        return buildCallableCreationMessage(!receiverTypeInfoOfBaseCallable.isEmpty(), renderedCallablesByKind)
    })

    /**
     * Returns true if any type candidate of [receiverInfo] has a class declaration that satisfies [checkReceiverTypeCandidate].
     */
    open fun anyDeclarationOfReceiverTypeCandidates(receiverInfo: TypeInfoBase, checkReceiverTypeCandidate: (PsiElement?) -> Boolean): Boolean = false

    private fun List<CallableInfo>.containsPropertyWithoutAnyContainers() = any { it is PropertyInfo && it.possibleContainers.isEmpty() }

    protected open val calculatedAvailableImpl: Boolean by lazy(fun(): Boolean {
        element ?: return false

        val callableInfos = notEmptyCallableInfos() ?: return false
        val callableInfo = callableInfos.first()
        val receiverInfo = callableInfo.receiverTypeInfo

        if (receiverInfo.isEmpty()) {
            if (callableInfos.containsPropertyWithoutAnyContainers()) return false
            // Since we don't have a receiver, it is available if the extension is not presumed.
            return !isExtension
        }

        val propertyInfo = callableInfos.firstOrNull { it is PropertyInfo } as PropertyInfo?
        val isFunction = callableInfos.any { it.kind == CallableKind.FUNCTION }
        return anyDeclarationOfReceiverTypeCandidates(receiverInfo) { declaration ->
            val insertToJavaInterface = declaration is PsiClass && declaration.isInterface

            // When it is a property:
            if (propertyInfo != null) {
                if (!isExtension && insertToJavaInterface && (!receiverInfo.staticContextRequired || propertyInfo.writable)) return@anyDeclarationOfReceiverTypeCandidates false
                if (!propertyInfo.isAbstract && declaration is KtClass && declaration.isInterface()) return@anyDeclarationOfReceiverTypeCandidates false
            }

            if (isFunction && insertToJavaInterface && receiverInfo.staticContextRequired) return@anyDeclarationOfReceiverTypeCandidates false

            /* When it is a type parameter, we accept only the callable creation with extension.
               For example:
                fun <T> foo(t: T) {
                    t.<caret>bar()
                }
               We cannot create a member function `bar()` of `T` because We don't know what `T` is. */
            if (declaration is KtTypeParameter && !isExtension) return@anyDeclarationOfReceiverTypeCandidates false

            declaration != null
        }
    })

    /**
     * Has to be invoked manually from final class ctor (as all final class properties have to be initialized)
     */
    protected fun init() {
        check(!initialized) { "${javaClass.simpleName} is already initialized" }
        this.element ?: return
        val callableInfos = callableInfos()
        if (callableInfos.size > 1) {
            val receiverSet = callableInfos.mapTo(HashSet()) { it.receiverTypeInfo }
            if (receiverSet.size > 1) throw AssertionError("All functions must have common receiver: $receiverSet")

            val possibleContainerSet = callableInfos.mapTo(HashSet()) { it.possibleContainers }
            if (possibleContainerSet.size > 1) throw AssertionError("All functions must have common containers: $possibleContainerSet")
        }
        initializeLazyProperties()
    }

    @Suppress("UNUSED_VARIABLE")
    private fun initializeLazyProperties() {
        // enforce lazy properties be calculated as QuickFix is created on a bg thread
        val text = calculatedText
        val availableImpl = calculatedAvailableImpl
        initialized = true
    }

    protected fun checkIsInitialized() {
        check(initialized) { "${javaClass.simpleName} is not initialized" }
    }

    override fun getText(): String {
        checkIsInitialized()
        element ?: return ""
        return calculatedText
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.create.from.usage.family")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean {
        checkIsInitialized()
        element ?: return false
        return calculatedAvailableImpl
    }
}
