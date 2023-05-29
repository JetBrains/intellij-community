// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisAllowanceManager
import org.jetbrains.kotlin.miniStdLib.annotations.PrivateForInline
import kotlin.experimental.ExperimentalTypeInference
import kotlin.reflect.KClass

/**
 * Applies a fix to the PSI, used as intention/inspection/quickfix action
 * Also, knows if a fix is applicable by [isApplicableByPsi]
 *
 * Uses some additional information from [INPUT] to apply the element
 */
@FileModifier.SafeTypeForPreview
sealed class KotlinApplicator<in PSI : PsiElement, in INPUT : KotlinApplicatorInput> {

    /**
     * Applies some fix to given [psi], can not use resolve, so all needed data should be precalculated and stored in [input]
     *
     * @param psi a [PsiElement] to apply fix to
     * @param input additional data needed to apply the fix
     */
    fun applyTo(psi: PSI, input: INPUT, project: Project, editor: Editor?) {
        applyToImpl(psi, input, project, editor)
    }

    /**
     * Checks if applicator is applicable to specific element, can not use resolve inside
     */
    fun isApplicableByPsi(psi: PSI, project: Project): Boolean =
        KtAnalysisAllowanceManager.forbidAnalysisInside("KotlinApplicator.isApplicableByPsi") {
            isApplicableByPsiImpl(psi)
        }

    /**
     * Action name which will be as text in inspections/intentions
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     */
    fun getActionName(psi: PSI, input: INPUT): @IntentionName String = getActionNameImpl(psi, input)


    /**
     * Family name which will be used in inspections/intentions
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getFamilyName
     */
    fun getFamilyName(): @IntentionFamilyName String = getFamilyNameImpl()


    protected abstract fun applyToImpl(psi: PSI, input: INPUT, project: Project, editor: Editor?)
    protected abstract fun isApplicableByPsiImpl(psi: PSI): Boolean
    protected abstract fun getActionNameImpl(psi: PSI, input: INPUT): @IntentionName String
    protected abstract fun getFamilyNameImpl(): @IntentionFamilyName String
}

/**
 * Create a copy of an applicator with some components replaced
 */
fun <PSI : PsiElement, NEW_PSI : PSI, INPUT : KotlinApplicatorInput> KotlinApplicator<PSI, INPUT>.with(
    init: KotlinApplicatorBuilder<NEW_PSI, INPUT>.(olApplicator: KotlinApplicator<PSI, INPUT>) -> Unit
): KotlinApplicator<NEW_PSI, INPUT> = when (this@with) {
    is KotlinApplicatorImpl -> {
        val builder = KotlinApplicatorBuilder(applyTo, isApplicableByPsi, getActionName, getFamilyName)
        @Suppress("UNCHECKED_CAST")
        init(builder as KotlinApplicatorBuilder<NEW_PSI, INPUT>, this)
        builder.build()
    }
}

/**
 * Create a copy of an applicator with some components replaced
 * The PSI type of a new applicator will be a class passed in [newPsiTypeTag]
 */
fun <PSI : PsiElement, NEW_PSI : PSI, INPUT : KotlinApplicatorInput> KotlinApplicator<PSI, INPUT>.with(
    @Suppress("UNUSED_PARAMETER") newPsiTypeTag: KClass<NEW_PSI>,
    init: KotlinApplicatorBuilder<NEW_PSI, INPUT>.(olApplicator: KotlinApplicator<PSI, INPUT>) -> Unit
): KotlinApplicator<NEW_PSI, INPUT> = when (this@with) {
    is KotlinApplicatorImpl -> {
        val builder = KotlinApplicatorBuilder(applyTo, isApplicableByPsi, getActionName, getFamilyName)
        @Suppress("UNCHECKED_CAST")
        init(builder as KotlinApplicatorBuilder<NEW_PSI, INPUT>, this)
        builder.build()
    }
}


internal class KotlinApplicatorImpl<PSI : PsiElement, INPUT : KotlinApplicatorInput>(
    val applyTo: (PSI, INPUT, Project, Editor?) -> Unit,
    val isApplicableByPsi: (PSI) -> Boolean,
    val getActionName: (PSI, INPUT) -> @IntentionName String,
    val getFamilyName: () -> @IntentionFamilyName String,
) : KotlinApplicator<PSI, INPUT>() {
    override fun applyToImpl(psi: PSI, input: INPUT, project: Project, editor: Editor?) {
        applyTo.invoke(psi, input, project, editor)
    }

    override fun isApplicableByPsiImpl(psi: PSI): Boolean =
        isApplicableByPsi.invoke(psi)

    override fun getActionNameImpl(psi: PSI, input: INPUT): String =
        getActionName.invoke(psi, input)

    override fun getFamilyNameImpl(): String =
        getFamilyName.invoke()
}


class KotlinApplicatorBuilder<PSI : PsiElement, INPUT : KotlinApplicatorInput> internal constructor(
    @property:PrivateForInline
    var applyTo: ((PSI, INPUT, Project, Editor?) -> Unit)? = null,
    private var isApplicableByPsi: ((PSI) -> Boolean)? = null,
    private var getActionName: ((PSI, INPUT) -> @IntentionName String)? = null,
    private var getFamilyName: (() -> @IntentionFamilyName String)? = null
) {
    fun familyName(getName: () -> @IntentionFamilyName String) {
        getFamilyName = getName
    }

    fun familyAndActionName(getName: () -> @NlsSafe String) {
        getFamilyName = getName
        getActionName = { _, _ -> getName() }
    }

    fun actionName(getActionName: (PSI, INPUT) -> @IntentionName String) {
        this.getActionName = getActionName
    }

    fun actionName(getActionName: () -> @IntentionName String) {
        this.getActionName = { _, _ -> getActionName() }
    }

    @OptIn(PrivateForInline::class)
    fun applyToWithEditorRequired(doApply: (PSI, INPUT, Project, Editor) -> Unit) {
        applyTo = { element, data, project, editor -> if (editor != null) doApply(element, data, project, editor) }
    }

    @OptIn(PrivateForInline::class)
    fun applyTo(doApply: (PSI, INPUT, Project, Editor?) -> Unit) {
        applyTo = doApply
    }

    @OptIn(PrivateForInline::class)
    fun applyTo(doApply: (PSI, INPUT) -> Unit) {
        applyTo = { element, data, _, _ -> doApply(element, data) }
    }

    @OptIn(PrivateForInline::class)
    fun applyTo(doApply: (PSI, INPUT, Project) -> Unit) {
        applyTo = { element, data, project, _ -> doApply(element, data, project) }
    }

    fun isApplicableByPsi(isApplicable: ((PSI) -> Boolean)? = null) {
        this.isApplicableByPsi = isApplicable
    }


    @OptIn(PrivateForInline::class)
    fun build(): KotlinApplicator<PSI, INPUT> {
        val applyTo = applyTo
            ?: error("Please, specify applyTo")
        val getActionName = getActionName
            ?: error("Please, specify actionName or familyName via either of: actionName,familyAndActionName")
        val isApplicableByPsi = isApplicableByPsi ?: { true }
        val getFamilyName = getFamilyName
            ?: error("Please, specify or familyName via either of: familyName, familyAndActionName")
        return KotlinApplicatorImpl(
            applyTo = applyTo,
            isApplicableByPsi = isApplicableByPsi,
            getActionName = getActionName,
            getFamilyName = getFamilyName
        )
    }
}


/**
 * Builds a new applicator with [KotlinApplicatorBuilder]
 *
 *  Should specify at least applyTo and familyAndActionName
 *
 *  @see KotlinApplicatorBuilder
 */
fun <PSI : PsiElement, INPUT : KotlinApplicatorInput> applicator(
    init: KotlinApplicatorBuilder<PSI, INPUT>.() -> Unit,
): KotlinApplicator<PSI, INPUT> =
    KotlinApplicatorBuilder<PSI, INPUT>().apply(init).build()
