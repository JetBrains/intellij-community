// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisAllowanceManager
import org.jetbrains.kotlin.miniStdLib.annotations.PrivateForInline
import kotlin.reflect.KClass

/**
 * Applies a fix to the PSI, used as intention/inspection/quickfix action
 * Also, knows if a fix is applicable by [isApplicableByPsi]
 *
 * Uses some additional information from [INPUT] to apply the element
 */
@FileModifier.SafeTypeForPreview
sealed interface KotlinApplicator<in PSI : PsiElement, in INPUT : KotlinApplicatorInput> :
    ReportingClassSubstitutor {

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
    fun isApplicableByPsiImpl(psi: PSI): Boolean
    fun getActionNameImpl(psi: PSI, input: INPUT): @IntentionName String
    fun getFamilyNameImpl(): @IntentionFamilyName String

    fun startInWriteAction(): Boolean = true
}

abstract class BaseKotlinApplicator<in PSI : PsiElement, in INPUT : KotlinApplicatorInput>: KotlinApplicator<PSI, INPUT> {

    /**
     * Applies some fix to given [psi], can not use resolve, so all needed data should be precalculated and stored in [input]
     *
     * @param psi a [PsiElement] to apply fix to
     * @param input additional data needed to apply the fix
     */
    fun applyTo(psi: PSI, input: INPUT, project: Project, editor: Editor?) {
        applyToImpl(psi, input, project, editor)
    }

    protected abstract fun applyToImpl(psi: PSI, input: INPUT, project: Project, editor: Editor?)

}

abstract class KotlinModCommandApplicator<in PSI : PsiElement, in INPUT : KotlinApplicatorInput>: KotlinApplicator<PSI, INPUT> {

    /**
     * Applies some fix to given [psi], can not use resolve, so all needed data should be precalculated and stored in [input]
     *
     * To be invoked on a background thread only.
     *
     * @param psi a non-physical [PsiElement] to apply fix to
     * @param input additional data needed to apply the fix
     */
    fun applyTo(psi: PSI, input: INPUT, context: ActionContext, updater: ModPsiUpdater) {
        applyToImpl(psi, input, context, updater)
    }


    protected abstract fun applyToImpl(psi: PSI, input: INPUT, context: ActionContext, updater: ModPsiUpdater)

}


/**
 * Create a copy of an applicator with some components replaced
 */
fun <PSI : PsiElement, NEW_PSI : PSI, INPUT : KotlinApplicatorInput> KotlinApplicator<PSI, INPUT>.with(
    init: AbstractKotlinApplicatorBuilder<NEW_PSI, INPUT>.(olApplicator: KotlinApplicator<PSI, INPUT>) -> Unit
): KotlinApplicator<NEW_PSI, INPUT> {
    when (this@with) {
        is BaseKotlinApplicatorImpl -> {
            val builder = KotlinApplicatorBuilder(init.javaClass, applyTo, isApplicableByPsi, getActionName, getFamilyName)
            @Suppress("UNCHECKED_CAST")
            init(builder as KotlinApplicatorBuilder<NEW_PSI, INPUT>, this)
            return builder.build()
        }
        is KotlinModCommandApplicatorImpl -> {
            val builder = KotlinModCommandApplicatorBuilder(init.javaClass, applyTo, isApplicableByPsi, getActionName, getFamilyName)
            @Suppress("UNCHECKED_CAST")
            init(builder as KotlinModCommandApplicatorBuilder<NEW_PSI, INPUT>, this)
            return builder.build()
        }
        else -> {
            error("unsupported $this@with")
        }
    }

}

/**
 * Create a copy of an applicator with some components replaced
 * The PSI type of new applicator will be a class passed in [newPsiTypeTag]
 */
fun <PSI : PsiElement, NEW_PSI : PSI, INPUT : KotlinApplicatorInput> KotlinApplicator<PSI, INPUT>.with(
    @Suppress("UNUSED_PARAMETER") newPsiTypeTag: KClass<NEW_PSI>,
    init: AbstractKotlinApplicatorBuilder<NEW_PSI, INPUT>.(olApplicator: KotlinApplicator<PSI, INPUT>) -> Unit
): KotlinApplicator<NEW_PSI, INPUT> =
    when (this@with) {
        is BaseKotlinApplicatorImpl -> {
            val builder = KotlinApplicatorBuilder(init.javaClass, applyTo, isApplicableByPsi, getActionName, getFamilyName)
            @Suppress("UNCHECKED_CAST")
            init(builder as KotlinApplicatorBuilder<NEW_PSI, INPUT>, this)
            builder.build()
        }

        is KotlinModCommandApplicatorImpl -> {
            val builder =
                KotlinModCommandApplicatorBuilder(init.javaClass, applyTo, isApplicableByPsi, getActionName, getFamilyName)
            @Suppress("UNCHECKED_CAST")
            init(builder as KotlinModCommandApplicatorBuilder<NEW_PSI, INPUT>, this)
            builder.build()
        }

        else -> error("unsupported $this@with")
    }

internal class BaseKotlinApplicatorImpl<PSI : PsiElement, INPUT : KotlinApplicatorInput>(
    private val reportingClass: Class<*>,
    val applyTo: (PSI, INPUT, Project, Editor?) -> Unit,
    internal val isApplicableByPsi: (PSI) -> Boolean,
    internal val getActionName: (PSI, INPUT) -> @IntentionName String,
    internal val getFamilyName: () -> @IntentionFamilyName String,
    internal val getStartInWriteAction: () -> Boolean = { true },
) : BaseKotlinApplicator<PSI, INPUT>() {
    override fun applyToImpl(psi: PSI, input: INPUT, project: Project, editor: Editor?) {
        applyTo.invoke(psi, input, project, editor)
    }

    override fun isApplicableByPsiImpl(psi: PSI): Boolean =
        isApplicableByPsi.invoke(psi)

    override fun getActionNameImpl(psi: PSI, input: INPUT): String =
        getActionName.invoke(psi, input)

    override fun getFamilyNameImpl(): String =
        getFamilyName.invoke()

    override fun getSubstitutedClass(): Class<*> =
        reportingClass

    override fun startInWriteAction(): Boolean {
        return getStartInWriteAction()
    }
}

internal class KotlinModCommandApplicatorImpl<PSI : PsiElement, INPUT : KotlinApplicatorInput>(
    private val reportingClass: Class<*>,
    internal val applyTo: (PSI, INPUT, context: ActionContext, updater: ModPsiUpdater) -> Unit,
    internal val isApplicableByPsi: (PSI) -> Boolean,
    internal val getActionName: (PSI, INPUT) -> @IntentionName String,
    internal val getFamilyName: () -> @IntentionFamilyName String,
    internal val getStartInWriteAction: () -> Boolean = { true },
) : KotlinModCommandApplicator<PSI, INPUT>() {
    override fun applyToImpl(psi: PSI, input: INPUT, context: ActionContext, updater: ModPsiUpdater) {
        applyTo.invoke(psi, input, context, updater)
    }


    override fun isApplicableByPsiImpl(psi: PSI): Boolean =
        isApplicableByPsi.invoke(psi)

    override fun getActionNameImpl(psi: PSI, input: INPUT): String =
        getActionName.invoke(psi, input)

    override fun getFamilyNameImpl(): String =
        getFamilyName.invoke()

    override fun getSubstitutedClass(): Class<*> =
        reportingClass

    override fun startInWriteAction(): Boolean {
        return getStartInWriteAction()
    }
}

abstract class AbstractKotlinApplicatorBuilder<PSI : PsiElement, INPUT : KotlinApplicatorInput> internal constructor(
    internal val reportingClass: Class<*>,
    internal var isApplicableByPsi: ((PSI) -> Boolean)? = null,
    internal var getActionName: ((PSI, INPUT) -> @IntentionName String)? = null,
    internal var getFamilyName: (() -> @IntentionFamilyName String)? = null,
    internal var getStartInWriteAction: () -> Boolean = { true },
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

    fun isApplicableByPsi(isApplicable: ((PSI) -> Boolean)? = null) {
        this.isApplicableByPsi = isApplicable
    }

    fun startInWriteAction(getStartInWriteAction: () -> Boolean) {
        this.getStartInWriteAction = getStartInWriteAction
    }

}

class KotlinApplicatorBuilder<PSI : PsiElement, INPUT : KotlinApplicatorInput> internal constructor(
    reportingClass: Class<*>,
    @property:PrivateForInline
    var applyTo: ((PSI, INPUT, Project, Editor?) -> Unit)? = null,
    isApplicableByPsi: ((PSI) -> Boolean)? = null,
    getActionName: ((PSI, INPUT) -> @IntentionName String)? = null,
    getFamilyName: (() -> @IntentionFamilyName String)? = null,
    getStartInWriteAction: () -> Boolean = { true },
): AbstractKotlinApplicatorBuilder<PSI, INPUT>(reportingClass, isApplicableByPsi, getActionName, getFamilyName, getStartInWriteAction) {

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

    @OptIn(PrivateForInline::class)
    fun build(): BaseKotlinApplicator<PSI, INPUT> {
        val getActionName = getActionName
            ?: error("Please, specify actionName or familyName via either of: actionName,familyAndActionName")
        val isApplicableByPsi = isApplicableByPsi ?: { true }
        val getFamilyName = getFamilyName
            ?: error("Please, specify or familyName via either of: familyName, familyAndActionName")

        val applyTo = applyTo ?: error("Please, specify applyTo")

        return BaseKotlinApplicatorImpl(
            reportingClass,
            applyTo = applyTo,
            isApplicableByPsi = isApplicableByPsi,
            getActionName = getActionName,
            getFamilyName = getFamilyName,
            getStartInWriteAction = getStartInWriteAction
        )
    }
}
class KotlinModCommandApplicatorBuilder<PSI : PsiElement, INPUT : KotlinApplicatorInput> internal constructor(
    reportingClass: Class<*>,
    @property:PrivateForInline
    var applyTo: ((PSI, INPUT, context: ActionContext, updater: ModPsiUpdater) -> Unit)? = null,
    isApplicableByPsi: ((PSI) -> Boolean)? = null,
    getActionName: ((PSI, INPUT) -> @IntentionName String)? = null,
    getFamilyName: (() -> @IntentionFamilyName String)? = null
): AbstractKotlinApplicatorBuilder<PSI, INPUT>(reportingClass, isApplicableByPsi, getActionName, getFamilyName) {

    @OptIn(PrivateForInline::class)
    fun applyTo(doApply: (PSI, INPUT, context: ActionContext, updater: ModPsiUpdater) -> Unit) {
        applyTo = doApply
    }

    @OptIn(PrivateForInline::class)
    fun applyTo(doApply: (PSI, INPUT) -> Unit) {
        applyTo = { element, data, _, _ -> doApply(element, data) }
    }

    @OptIn(PrivateForInline::class)
    fun applyTo(doApply: (PSI) -> Unit) {
        applyTo = { element, _, _, _ -> doApply(element) }
    }

    @OptIn(PrivateForInline::class)
    fun build(): KotlinModCommandApplicator<PSI, INPUT> {
        val applyTo = applyTo ?: error("Please, specify applyTo")
        val getActionName = getActionName
            ?: error("Please, specify actionName or familyName via either of: actionName,familyAndActionName")
        val isApplicableByPsi = isApplicableByPsi ?: { true }
        val getFamilyName = getFamilyName
            ?: error("Please, specify or familyName via either of: familyName, familyAndActionName")

        return KotlinModCommandApplicatorImpl(
            reportingClass,
            applyTo = applyTo,
            isApplicableByPsi = isApplicableByPsi,
            getActionName = getActionName,
            getFamilyName = getFamilyName
        )
    }
}


/**
 * Builds a new applicator with [BaseKotlinApplicator]
 *
 *  Should specify at least applyTo and familyAndActionName
 *
 *  @see BaseKotlinApplicator
 */
@Deprecated("prefer modCommandApplicator", replaceWith = ReplaceWith("modCommandApplicator"))
fun <PSI : PsiElement, INPUT : KotlinApplicatorInput> applicator(
    init: KotlinApplicatorBuilder<PSI, INPUT>.() -> Unit,
): BaseKotlinApplicator<PSI, INPUT> =
    KotlinApplicatorBuilder<PSI, INPUT>(init.javaClass).apply(init).build()


/**
 * Builds a new applicator with [KotlinModCommandApplicatorBuilder]
 *
 *  Should specify at least applyTo and familyAndActionName
 *
 *  @see KotlinModCommandApplicatorBuilder
 */
fun <PSI : PsiElement, INPUT : KotlinApplicatorInput> modCommandApplicator(
    init: KotlinModCommandApplicatorBuilder<PSI, INPUT>.() -> Unit,
): KotlinModCommandApplicator<PSI, INPUT> =
    KotlinModCommandApplicatorBuilder<PSI, INPUT>(init.javaClass).apply(init).build()
