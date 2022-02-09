// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.applicators

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtReferenceShortenerMixIn
import org.jetbrains.kotlin.analysis.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.intentions.ChooseValueExpression
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.shortenReferences
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

object CallableReturnTypeUpdaterApplicator {
    val applicator = applicator<KtCallableDeclaration, TypeInfo> {
        familyAndActionName(KotlinBundle.lazyMessage("fix.change.return.type.family"))

        applyTo { declaration, typeInfo, project, editor ->
            if (editor == null || !typeInfo.useTemplate) {
                declaration.setType(typeInfo.defaultType, project)
            } else {
                setTypeWithTemplate(listOf(declaration to typeInfo).iterator(), project, editor)
            }
        }
    }

    private fun KtCallableDeclaration.setType(type: TypeInfo.Type, project: Project) {
        val newTypeRef = if (isProcedure(type)) {
            null
        } else {
            KtPsiFactory(project).createType(type.longTypeRepresentation)
        }
        runWriteAction {
            typeReference = newTypeRef
            typeReference?.let { shortenReferences(it) }
        }
    }

    private fun KtCallableDeclaration.isProcedure(type: TypeInfo.Type) =
        type.isUnit && this is KtFunction && hasBlockBody()


    /**
     * @param declarationAndTypes multiple declarations and types that need to be updated. If multiple pairs are passed, the IDE will guide
     * user to modify them one by one.
     */
    // TODO: add applicator that passes multiple declarations and types, for example, for specifying types of destructuring declarations.
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

    class TypeInfo(
        val defaultType: Type,
        val otherTypes: List<Type> = emptyList(),
        val useTemplate: Boolean = false,
    ) : HLApplicatorInput {
        class Type(val isUnit: Boolean, val longTypeRepresentation: String, val shortTypeRepresentation: String)

        override fun isValidFor(psi: PsiElement): Boolean = true

        companion object {
            fun KtAnalysisSession.createByKtTypes(
                ktType: KtType,
                otherTypes: List<KtType> = emptyList(),
                useTemplate: Boolean = false
            ): TypeInfo = TypeInfo(createTypeByKtType(ktType), otherTypes.map { createTypeByKtType(it) }, useTemplate)

            private fun KtAnalysisSession.createTypeByKtType(ktType: KtType): Type = Type(
                isUnit = ktType.isUnit,
                longTypeRepresentation = ktType.render(KtTypeRendererOptions.DEFAULT),
                shortTypeRepresentation = ktType.render(KtTypeRendererOptions.SHORT_NAMES),
            )

            val UNIT = Type(isUnit = true, longTypeRepresentation = "kotlin.Unit", shortTypeRepresentation = "Unit")
            val ANY = Type(isUnit = false, longTypeRepresentation = "kotlin.Any", shortTypeRepresentation = "Any")
        }
    }
}
