// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseValueExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo.Companion.createByKtTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.bfs

object CallableReturnTypeUpdaterUtils {
    fun updateType(
        declaration: KtCallableDeclaration,
        typeInfo: TypeInfo,
        project: Project,
        editor: Editor? = null,
        updater: ModPsiUpdater? = null
    ) {
        if (editor == null || !typeInfo.useTemplate || !ApplicationManager.getApplication().isWriteAccessAllowed) {
            declaration.setType(typeInfo.defaultType, project, updater)
        } else {
            setTypeWithTemplate(listOf(declaration to typeInfo).iterator(), project, editor)
        }

        if (declaration is KtProperty && !typeInfo.useTemplate) {
            val getter = declaration.getter
            val returnTypeReference = getter?.returnTypeReference
            if (returnTypeReference != null && !typeInfo.defaultType.isUnit) {
                val typeRef = shortenReferences(returnTypeReference.replace(KtPsiFactory(project).createType(typeInfo.defaultType.longTypeRepresentation)) as KtElement)
                if (typeRef != null) {
                    updater?.moveCaretTo(typeRef.endOffset)
                }
            }

            val setterParameter = declaration.setter?.parameter
            if (setterParameter?.typeReference != null) {
                updateType(setterParameter, typeInfo, project, editor, updater)
            }
        }
    }

    private fun KtCallableDeclaration.setType(type: TypeInfo.Type, project: Project, updater: ModPsiUpdater? = null) {
        val newTypeRef = if (isProcedure(type)) {
            null
        } else {
            KtPsiFactory(project).createType(type.longTypeRepresentation)
        }
        typeReference = newTypeRef
        typeReference?.let {
            shortenReferences(it)
            updater?.moveCaretTo(it.endOffset)
        }
    }

    private fun KtCallableDeclaration.isProcedure(type: TypeInfo.Type) =
        type.isUnit && this is KtFunction && hasBlockBody()

    /**
     * @param declarationAndTypes multiple declarations and types that need to be updated. If multiple pairs are passed, the IDE will guide
     * user to modify them one by one.
     */
    // TODO: add `updateType` that passes multiple declarations and types, for example, for specifying types of destructuring declarations.
    private fun setTypeWithTemplate(
        declarationAndTypes: Iterator<Pair<KtCallableDeclaration, TypeInfo>>,
        project: Project,
        editor: Editor
    ) {
        if (!declarationAndTypes.hasNext()) return
        val (declaration: KtCallableDeclaration, typeInfo: TypeInfo) = declarationAndTypes.next()
        // Set a placeholder type so that it can be referenced
        declaration.setType(TypeInfo.ANY, project)
        PsiDocumentManager.getInstance(project).apply {
            commitAllDocuments()
            doPostponedOperationsAndUnblockDocument(editor.document)
        }

        val newTypeRef = declaration.typeReference ?: return
        val builder = TemplateBuilderImpl(newTypeRef)
        builder.replaceElement(
            newTypeRef,
            TypeChooseValueExpression(listOf(typeInfo.defaultType) + typeInfo.otherTypes, typeInfo.defaultType)
        )

        editor.caretModel.moveToOffset(newTypeRef.node.startOffset)

        TemplateManager.getInstance(project).startTemplate(
            editor,
            builder.buildInlineTemplate(),
            object : TemplateEditingAdapter() {
                override fun templateFinished(template: Template, brokenOff: Boolean) {
                    val typeRef = declaration.typeReference
                    if (typeRef != null && typeRef.isValid) {
                        runWriteAction {
                            shortenReferences(typeRef)
                            setTypeWithTemplate(declarationAndTypes, project, editor)
                        }
                    }
                }
            }
        )
    }

    private class TypeChooseValueExpression(
        items: List<TypeInfo.Type>, defaultItem: TypeInfo.Type
    ) : ChooseValueExpression<TypeInfo.Type>(items, defaultItem) {
        override fun getLookupString(element: TypeInfo.Type): String = element.shortTypeRepresentation
        override fun getResult(element: TypeInfo.Type): String = element.longTypeRepresentation
    }

    context(KtAnalysisSession)
    fun getTypeInfo(declaration: KtCallableDeclaration): TypeInfo {
        val declarationType = declaration.getReturnKtType()
        val overriddenTypes = (declaration.getSymbol() as? KtCallableSymbol)?.getDirectlyOverriddenSymbols()
            ?.map { it.returnType }
            ?.distinct()
            ?: emptyList()
        val cannotBeNull = overriddenTypes.any { !it.canBeNull }
        val allTypes = (listOf(declarationType) + overriddenTypes)
            // Here we do BFS manually rather than invoke `getAllSuperTypes` because we have multiple starting points. Simply calling
            // `getAllSuperTypes` does not work because it would BFS traverse each starting point and put the result together, in which
            // case, for example, calling `getAllSuperTypes` would put `Any` at middle if one of the super type in the hierarchy has
            // multiple super types.
            .bfs { it.getDirectSuperTypes(shouldApproximate = true).iterator() }
            .map { it.approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = true) }
            .distinct()
            .let { types ->
                when {
                    cannotBeNull -> types.map { it.withNullability(KtTypeNullability.NON_NULLABLE) }.distinct()
                    declarationType.hasFlexibleNullability -> types.flatMap { type ->
                        listOf(type.withNullability(KtTypeNullability.NON_NULLABLE), type.withNullability(KtTypeNullability.NULLABLE))
                    }

                    else -> types
                }
            }.toList()

        return with(TypeInfo) {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                selectForUnitTest(declaration, allTypes)?.let { return it }
            }

            val approximatedDefaultType = declarationType.approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = true).let {
                if (cannotBeNull) it.withNullability(KtTypeNullability.NON_NULLABLE)
                else it
            }
            createByKtTypes(
                approximatedDefaultType,
                allTypes.drop(1), // The first type is always the default type so we drop it.
                useTemplate = true
            )
        }
    }

    // The following logic is copied from FE1.0 at
    // org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention.Companion#createTypeExpressionForTemplate
    context(KtAnalysisSession)
    private fun selectForUnitTest(
        declaration: KtCallableDeclaration,
        allTypes: List<KtType>
    ): TypeInfo? {
        // This helps to be sure no nullable types are suggested
        if (declaration.containingKtFile.findDescendantOfType<PsiComment>()?.takeIf {
                it.text == "// CHOOSE_NULLABLE_TYPE_IF_EXISTS"
            } != null) {
            val targetType = allTypes.firstOrNull { it.isMarkedNullable } ?: allTypes.first()
            return createByKtTypes(targetType)
        }
        // This helps to be sure something except Nothing is suggested
        if (declaration.containingKtFile.findDescendantOfType<PsiComment>()?.takeIf {
                it.text == "// DO_NOT_CHOOSE_NOTHING"
            } != null
        ) {
            // Note that `isNothing` returns true for both `Nothing` and `Nothing?`
            val targetType = allTypes.firstOrNull { !it.isNothing } ?: allTypes.first()
            return createByKtTypes(targetType)
        }
        return null
    }

    class TypeInfo(
        val defaultType: Type,
        val otherTypes: List<Type> = emptyList(),
        val useTemplate: Boolean = false,
    ) : KotlinApplicatorInput {
        class Type(val isUnit: Boolean, val isError: Boolean, val longTypeRepresentation: String, val shortTypeRepresentation: String)

        override fun isValidFor(psi: PsiElement): Boolean = true

        companion object {
            context(KtAnalysisSession)
            fun createByKtTypes(
                ktType: KtType,
                otherTypes: List<KtType> = emptyList(),
                useTemplate: Boolean = false
            ): TypeInfo = TypeInfo(createTypeByKtType(ktType), otherTypes.map { createTypeByKtType(it) }, useTemplate)

            context(KtAnalysisSession)
            private fun createTypeByKtType(ktType: KtType): Type = Type(
                isUnit = ktType.isUnit,
                isError = ktType is KtErrorType,
                longTypeRepresentation = ktType.render(KtTypeRendererForSource.WITH_QUALIFIED_NAMES, position = Variance.OUT_VARIANCE),
                shortTypeRepresentation = ktType.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE),
            )

            val UNIT = Type(isUnit = true, isError = false, longTypeRepresentation = "kotlin.Unit", shortTypeRepresentation = "Unit")
            val ANY = Type(isUnit = false, isError = false, longTypeRepresentation = "kotlin.Any", shortTypeRepresentation = "Any")
        }
    }
}
