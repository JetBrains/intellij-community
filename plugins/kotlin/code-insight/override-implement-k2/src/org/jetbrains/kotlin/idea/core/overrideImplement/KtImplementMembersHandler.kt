// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.components.getImplementationStatus
import org.jetbrains.kotlin.analysis.api.components.intersectionOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.components.isVisibleInClass
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler.Companion.getUnimplementedMembers
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.idea.search.ExpectActualSupport
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.util.ImplementationStatus

@ApiStatus.Internal
open class KtImplementMembersHandler : KtGenerateMembersHandler(true) {
    override fun getChooserTitle(): String = KotlinIdeaCoreBundle.message("implement.members.handler.title")

    override fun getNoMembersFoundHint(): String = KotlinIdeaCoreBundle.message("implement.members.handler.no.members.hint")

    override fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<KtClassMember> {
        return analyze(classOrObject) {
            getUnimplementedMembers(classOrObject).map { createKtClassMember(it, BodyType.FromTemplate, false) }
        }
    }

    @OptIn(KaExperimentalApi::class)
    fun collectMembersToAddOverride(classOrObject: KtClassOrObject): List<KtCallableDeclaration> {
        analyze(classOrObject) {
            val classSymbol = classOrObject.classSymbol
            return classSymbol?.memberScope?.callables?.toList()?.mapNotNull { symbol ->
                (symbol.psi as? KtCallableDeclaration)?.takeIf {
                    symbol.getImplementationStatus(classSymbol) == ImplementationStatus.CANNOT_BE_IMPLEMENTED
                }
            } ?: emptyList()
        }
    }

    companion object {
        context(_: KaSession)
        fun getUnimplementedMembers(classWithUnimplementedMembers: KtClassOrObject): List<KtClassMemberInfo> =
            classWithUnimplementedMembers.classSymbol?.let { getUnimplementedMemberSymbols(it) }.orEmpty()
                .mapToKtClassMemberInfo()

        context(_: KaSession)
        @OptIn(KaExperimentalApi::class)
        private fun getUnimplementedMemberSymbols(classWithUnimplementedMembers: KaClassSymbol): List<KaCallableSymbol> {
            return buildList {
                classWithUnimplementedMembers.memberScope.callables.forEach { symbol ->
                    if (!symbol.isVisibleInClass(classWithUnimplementedMembers)) return@forEach
                    when (symbol.getImplementationStatus(classWithUnimplementedMembers)) {
                        ImplementationStatus.NOT_IMPLEMENTED -> add(symbol)
                        ImplementationStatus.AMBIGUOUSLY_INHERITED,
                        ImplementationStatus.INHERITED_OR_SYNTHESIZED -> {
                            val intersectionOverriddenSymbols = symbol.intersectionOverriddenSymbols
                            val (abstractSymbols, nonAbstractSymbols) = intersectionOverriddenSymbols.partition {
                                it.modality == KaSymbolModality.ABSTRACT
                            }
                            if (isManyMemberNotImplementedError(intersectionOverriddenSymbols)) {
                                // This for the [MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED] and [MANY_IMPL_MEMBER_NOT_IMPLEMENTED] compiler errors.
                                addAll(abstractSymbols.ifEmpty { intersectionOverriddenSymbols })
                            } else if (abstractSymbols.isNotEmpty() && nonAbstractSymbols.isEmpty()) {
                                addAll(abstractSymbols)
                            }
                        }

                        else -> {
                        }
                    }
                }
            }
        }
    }
}

internal class KtImplementMembersQuickfix(private val members: Collection<KtClassMemberInfo>) : KtImplementMembersHandler(),
                                                                                                IntentionAction {
    override fun getText() = familyName
    override fun getFamilyName() = KotlinIdeaCoreBundle.message("implement.members.handler.family")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = true

    override fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<KtClassMember> {
        return members.map { createKtClassMember(it, BodyType.FromTemplate, false) }
    }
}

internal class KtImplementAsConstructorParameterQuickfix(private val members: Collection<KtClassMemberInfo>) : KtImplementMembersHandler(),
                                                                                                               IntentionAction {
    override fun getText() = KotlinIdeaCoreBundle.message("action.text.implement.as.constructor.parameters")

    override fun getFamilyName() = KotlinIdeaCoreBundle.message("implement.members.handler.family")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = true

    override fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<KtClassMember> {
        return members.filter { it.isProperty }.map { createKtClassMember(it, BodyType.FromTemplate, true) }
    }
}

object MemberNotImplementedQuickfixFactories {

    val abstractMemberNotImplemented: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.AbstractMemberNotImplemented> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AbstractMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi)
        }

    val abstractClassMemberNotImplemented: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.AbstractClassMemberNotImplemented> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AbstractClassMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi)
        }

    val manyInterfacesMemberNotImplemented: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.ManyInterfacesMemberNotImplemented> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ManyInterfacesMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi)
        }

    val manyImplMemberNotImplemented: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.ManyImplMemberNotImplemented> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ManyImplMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi, false)
        }

    val abstractMemberNotImplementedByEnumEntry: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.AbstractMemberNotImplementedByEnumEntry> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AbstractMemberNotImplementedByEnumEntry ->
            val missingDeclarations = diagnostic.missingDeclarations
            if (missingDeclarations.isEmpty()) return@IntentionBased emptyList()
            listOf(KtImplementMembersQuickfix(missingDeclarations.mapToKtClassMemberInfo()))
        }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    private fun getUnimplementedMemberFixes(
        classWithUnimplementedMembers: KtClassOrObject,
        includeImplementAsConstructorParameterQuickfix: Boolean = true
    ): List<IntentionAction> {
        val unimplementedMembers = getUnimplementedMembers(classWithUnimplementedMembers)
        if (unimplementedMembers.isEmpty()) return emptyList()

        return buildList {
            add(KtImplementMembersQuickfix(unimplementedMembers))
            if (includeImplementAsConstructorParameterQuickfix && classWithUnimplementedMembers is KtClass &&
                classWithUnimplementedMembers !is KtEnumEntry &&
                !classWithUnimplementedMembers.isInterface() &&
                !classWithUnimplementedMembers.isExpectDeclaration() &&
                !(classWithUnimplementedMembers.hasActualModifier() && (ExpectActualSupport.getInstance(classWithUnimplementedMembers.project)
                    .expectDeclarationIfAny(classWithUnimplementedMembers) as? KtClass)?.primaryConstructor != null)
            ) {
                val unimplementedProperties = unimplementedMembers.filter { memberInfo -> memberInfo.isProperty && with(session) { memberInfo.symbolPointer.restoreSymbol() }?.let { symbol -> symbol.contextParameters.isEmpty() && symbol.receiverParameter == null } != false }
                if (unimplementedProperties.isNotEmpty()) {
                    add(KtImplementAsConstructorParameterQuickfix(unimplementedProperties))
                }
            }
        }
    }
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
private fun List<KaCallableSymbol>.mapToKtClassMemberInfo(): List<KtClassMemberInfo> {
    return map { unimplementedMemberSymbol ->
        val containingSymbol = unimplementedMemberSymbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol

        @NlsSafe
        val fqName = (containingSymbol?.classId?.asSingleFqName()?.toString() ?: containingSymbol?.name?.asString())
        KtClassMemberInfo.create(
            symbol = unimplementedMemberSymbol,
            memberText = unimplementedMemberSymbol.render(KtGenerateMembersHandler.renderer),
            memberIcon = KotlinIconProvider.getIcon(unimplementedMemberSymbol),
            containingSymbolText = fqName,
            containingSymbolIcon = containingSymbol?.let { symbol -> KotlinIconProvider.getIcon(symbol) }
        )
    }
}

context(_: KaSession)
private fun isManyMemberNotImplementedError(callableSymbols: Collection<KaCallableSymbol>): Boolean {
    if (callableSymbols.size < 2) return false

    // No compiler errors occur here:
    // open class Shape {
    //     open fun draw(): Unit = Unit
    // }
    //
    // interface Drawable {
    //     fun draw()
    // }
    //
    // class Circle : Shape(), Drawable
    val singleOpenMethod = callableSymbols.singleOrNull { it.modality == KaSymbolModality.OPEN } ?: return true
    return (singleOpenMethod.containingSymbol as? KaClassSymbol)?.classKind != KaClassKind.CLASS
}
