// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.KtIconProvider.getIcon
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler.Companion.getUnimplementedMembers
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.util.ImplementationStatus

@ApiStatus.Internal
open class KtImplementMembersHandler : KtGenerateMembersHandler(true) {
    override fun getChooserTitle() = KotlinIdeaCoreBundle.message("implement.members.handler.title")

    override fun getNoMembersFoundHint() = KotlinIdeaCoreBundle.message("implement.members.handler.no.members.hint")

    override fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<KtClassMember> {
        return analyze(classOrObject) {
            getUnimplementedMembers(classOrObject).map { createKtClassMember(it, BodyType.FromTemplate, false) }
        }
    }

    companion object {
        context(KaSession)
        fun getUnimplementedMembers(classWithUnimplementedMembers: KtClassOrObject): List<KtClassMemberInfo> =
            classWithUnimplementedMembers.classSymbol?.let { getUnimplementedMemberSymbols(it) }.orEmpty()
                .mapToKtClassMemberInfo()

        context(KaSession)
        @OptIn(KaExperimentalApi::class)
        private fun getUnimplementedMemberSymbols(classWithUnimplementedMembers: KaClassSymbol): List<KaCallableSymbol> {
            return buildList {
                classWithUnimplementedMembers.memberScope.callables.forEach { symbol ->
                    if (!symbol.isVisibleInClass(classWithUnimplementedMembers)) return@forEach
                    when (symbol.getImplementationStatus(classWithUnimplementedMembers)) {
                        ImplementationStatus.NOT_IMPLEMENTED -> add(symbol)
                        ImplementationStatus.AMBIGUOUSLY_INHERITED,
                        ImplementationStatus.INHERITED_OR_SYNTHESIZED -> {
                            // This case is to show user abstract members that don't need to be implemented because another super class has provide
                            // an implementation. For example, given the following
                            //
                            // interface A { fun foo() }
                            // interface B { fun foo() {} }
                            // class Foo : A, B {}
                            //
                            // `Foo` does not need to implement `foo` since it inherits the implementation from `B`. But in the dialog, we should
                            // allow user to choose `foo` to implement.
                            val intersectionOverriddenSymbols = symbol.intersectionOverriddenSymbols
                            val abstractSymbols = intersectionOverriddenSymbols.filter {
                                it.modality == KaSymbolModality.ABSTRACT
                            }
                            if (abstractSymbols.isNotEmpty()) {
                                addAll(abstractSymbols)
                            } else {
                                // This for the [MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED] and [MANY_IMPL_MEMBER_NOT_IMPLEMENTED] compiler errors.
                                addAll(intersectionOverriddenSymbols)
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

    val abstractMemberNotImplemented =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AbstractMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi)
        }

    val abstractClassMemberNotImplemented =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AbstractClassMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi)
        }

    val manyInterfacesMemberNotImplemented =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ManyInterfacesMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi)
        }

    val manyImplMemberNotImplemented =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ManyImplMemberNotImplemented ->
            getUnimplementedMemberFixes(diagnostic.psi, false)
        }

    val abstractMemberNotImplementedByEnumEntry =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.AbstractMemberNotImplementedByEnumEntry ->
            val missingDeclarations = diagnostic.missingDeclarations
            if (missingDeclarations.isEmpty()) return@IntentionBased emptyList()
            listOf(KtImplementMembersQuickfix(missingDeclarations.mapToKtClassMemberInfo()))
        }

    context(KaSession)
    private fun getUnimplementedMemberFixes(
        classWithUnimplementedMembers: KtClassOrObject,
        includeImplementAsConstructorParameterQuickfix: Boolean = true
    ): List<IntentionAction> {
        val unimplementedMembers = getUnimplementedMembers(classWithUnimplementedMembers)
        if (unimplementedMembers.isEmpty()) return emptyList()

        return buildList {
            add(KtImplementMembersQuickfix(unimplementedMembers))
            if (includeImplementAsConstructorParameterQuickfix && classWithUnimplementedMembers is KtClass && classWithUnimplementedMembers !is KtEnumEntry && !classWithUnimplementedMembers.isInterface()) {
                // TODO: when MPP support is ready, return false if this class is `actual` and any expect classes have primary constructor.
                val unimplementedProperties = unimplementedMembers.filter { it.isProperty }
                if (unimplementedProperties.isNotEmpty()) {
                    add(KtImplementAsConstructorParameterQuickfix(unimplementedProperties))
                }
            }
        }
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun List<KaCallableSymbol>.mapToKtClassMemberInfo(): List<KtClassMemberInfo> {
    return map { unimplementedMemberSymbol ->
        val containingSymbol = unimplementedMemberSymbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol

        @NlsSafe
        val fqName = (containingSymbol?.classId?.asSingleFqName()?.toString() ?: containingSymbol?.name?.asString())
        KtClassMemberInfo.create(
            symbol = unimplementedMemberSymbol,
            memberText = unimplementedMemberSymbol.render(KtGenerateMembersHandler.renderer),
            memberIcon = getIcon(unimplementedMemberSymbol),
            containingSymbolText = fqName,
            containingSymbolIcon = containingSymbol?.let { symbol -> getIcon(symbol) }
        )
    }
}