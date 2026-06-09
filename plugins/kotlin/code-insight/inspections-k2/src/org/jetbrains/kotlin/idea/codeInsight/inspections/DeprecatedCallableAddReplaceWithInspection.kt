// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiRecursiveVisitor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveCompanionObjectShortReferenceToContainingClassSymbol
import org.jetbrains.kotlin.idea.imports.KotlinIdeDefaultImportProvider
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.resolve.ImportPath

internal class DeprecatedCallableAddReplaceWithInspection :
    KotlinApplicableInspectionBase.Simple<KtCallableDeclaration, DeprecatedCallableAddReplaceWithInspection.ReplaceWithData>() {

    data class ReplaceWithData(val expression: String, val imports: List<String>)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }

        override fun visitProperty(property: KtProperty) {
            if (property.isVar) return
            visitTargetElement(property, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean =
        element.annotationEntries.any { it.shortName?.asString() == DEPRECATED_NAME }

    override fun getApplicableRanges(element: KtCallableDeclaration): List<TextRange> =
        element.deprecatedAnnotationEntry()?.let { listOfNotNull(it.atSymbol?.textRangeIn(element), it.typeReference?.textRangeIn(element)) }.orEmpty()

    override fun getProblemDescription(element: KtCallableDeclaration, context: ReplaceWithData): String =
        KotlinBundle.message("deprecated.annotation.without.replacewith.argument")

    override fun createQuickFix(
        element: KtCallableDeclaration,
        context: ReplaceWithData,
    ): KotlinModCommandQuickFix<KtCallableDeclaration> = AddReplaceWithFix(context)

    override fun KaSession.prepareContext(element: KtCallableDeclaration): ReplaceWithData? {
        element.symbol.deprecatedAnnotationWithNoReplaceWith() ?: return null
        val replacementExpression = element.suggestReplacementExpression() ?: return null
        return buildReplaceWithData(replacementExpression)
    }

    context(_: KaSession)
    private fun buildReplaceWithData(replacementExpression: KtExpression): ReplaceWithData? {
        val file = replacementExpression.containingKtFile
        val currentPackageFqName = file.packageFqName
        val defaultImportProvider = KotlinIdeDefaultImportProvider.getInstance()
        val imports = linkedSetOf<String>()
        var isGood = true

        replacementExpression.accept(object : KtVisitorVoid(), PsiRecursiveVisitor {
            override fun visitReturnExpression(expression: KtReturnExpression) {
                isGood = false
            }

            override fun visitDeclaration(declaration: KtDeclaration) {
                isGood = false
            }

            override fun visitBlockExpression(expression: KtBlockExpression) {
                if (expression.statements.size > 1) {
                    isGood = false
                    return
                }

                super.visitBlockExpression(expression)
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val symbol = (
                    expression.mainReference.resolveCompanionObjectShortReferenceToContainingClassSymbol()
                        ?: expression.mainReference.resolveToSymbol()
                    ) as? KaDeclarationSymbol ?: return
                if (symbol.visibility == KaSymbolVisibility.PRIVATE) {
                    isGood = false
                    return
                }

                val requiresImport = (symbol as? KaCallableSymbol)?.isExtension == true || expression.getReceiverExpression() == null
                if (!requiresImport) return

                val fqName = symbol.importableFqName ?: return
                if (
                    !defaultImportProvider.isImportedWithDefault(ImportPath(fqName, false), file) &&
                    fqName.parent() != currentPackageFqName
                ) {
                    imports += fqName.asString()
                }
            }

            override fun visitKtElement(element: KtElement) {
                if (isGood) {
                    element.acceptChildren(this)
                }
            }
        })

        if (!isGood) return null

        return ReplaceWithData(
            expression = replacementExpression.toSingleLineExpressionText() ?: return null,
            imports = imports.toList(),
        )
    }

    private fun KtCallableDeclaration.deprecatedAnnotationEntry(): KtAnnotationEntry? =
        annotationEntries.firstOrNull { it.shortName?.asString() == DEPRECATED_NAME }

    context(_: KaSession)
    private fun KtCallableDeclaration.suggestReplacementExpression(): KtExpression? = when (this) {
        is KtNamedFunction -> replacementExpressionFromBody(returnType.isUnitType)
        is KtProperty -> getter?.replacementExpressionFromBody(returnType.isUnitType)
        else -> null
    }

    private fun KtDeclarationWithBody.replacementExpressionFromBody(returnsUnit: Boolean): KtExpression? {
        val body = bodyExpression ?: return null
        if (!hasBlockBody()) return body

        val block = body as? KtBlockExpression ?: return null
        val statement = block.statements.singleOrNull() ?: return null
        return when (statement) {
            is KtReturnExpression -> statement.returnedExpression
            else -> statement.takeIf { returnsUnit }
        }
    }

    context(_: KaSession)
    private fun KaDeclarationSymbol.deprecatedAnnotationWithNoReplaceWith(): KaAnnotation? {
        val annotation = annotations.find { it.classId?.asSingleFqName() == DEPRECATED_FQ_NAME } ?: return null
        if ((annotation.argumentValueByName(REPLACE_WITH_ARGUMENT_NAME) as? KaAnnotationValue.NestedAnnotationValue)?.annotation?.arguments?.isNotEmpty() == true) {
            return null
        }

        val level = annotation.argumentValueByName(LEVEL_ARGUMENT_NAME) as? KaAnnotationValue.EnumEntryValue
        if (level?.callableId?.callableName?.asString() == HIDDEN_LEVEL_NAME) return null

        return annotation
    }

    private fun KtExpression.toSingleLineExpressionText(): String? {
        val expression = try {
            KtPsiFactory(project).createExpression(text.replace('\n', ' '))
        } catch (_: Throwable) {
            return null
        }

        val string = (expression.reformatted(true) as KtExpression).text
        return string.split("\n").joinToString(" ") { it.trim() }.replace('\n', ' ')
    }

    private fun KaAnnotation.argumentValueByName(name: String): KaAnnotationValue? =
        arguments.firstOrNull { it.name.asString() == name }?.expression

    private inner class AddReplaceWithFix(private val replaceWithData: ReplaceWithData) : KotlinModCommandQuickFix<KtCallableDeclaration>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("add.replacewith.argument.to.specify.replacement.pattern")

        override fun applyFix(project: Project, element: KtCallableDeclaration, updater: ModPsiUpdater) {
            val annotationEntry = element.deprecatedAnnotationEntry() ?: return
            val valueArgumentList = annotationEntry.valueArgumentList ?: return
            val psiFactory = KtPsiFactory(project)

            var argument = psiFactory.createArgument(psiFactory.createExpression(annotationEntry.buildReplaceWithArgumentText(replaceWithData)))
            argument = valueArgumentList.addArgument(argument)
            shortenReferences(argument)?.let {
                updater.moveCaretTo(it)
            }
        }
    }

    private fun KtAnnotationEntry.buildReplaceWithArgumentText(replaceWithData: ReplaceWithData): String {
        val escapedText = replaceWithData.expression.escapeForStringLiteral()
        return buildString {
            if (valueArguments.any { it.isNamed() }) append("replaceWith = ")
            append("kotlin.ReplaceWith(\"")
            append(escapedText)
            append("\"")
            replaceWithData.imports.forEach { append(",\"").append(it).append("\"") }
            append(")")
        }
    }

    private fun String.escapeForStringLiteral(): String {
        var escapedText = replace("\\", "\\\\").replace("\"", "\\\"")
        if (!escapedText.contains('$')) return escapedText

        escapedText = buildString {
            var index = 0
            while (index < escapedText.length) {
                val character = escapedText[index++]
                if (character == '$' && index < escapedText.length) {
                    val nextCharacter = escapedText[index]
                    if (nextCharacter.isJavaIdentifierStart() || nextCharacter == '{') {
                        append('\\')
                    }
                }
                append(character)
            }
        }

        return escapedText
    }
}

private const val DEPRECATED_NAME = "Deprecated"
private val DEPRECATED_FQ_NAME = org.jetbrains.kotlin.builtins.StandardNames.FqNames.deprecated
private const val REPLACE_WITH_ARGUMENT_NAME = "replaceWith"
private const val LEVEL_ARGUMENT_NAME = "level"
private const val HIDDEN_LEVEL_NAME = "HIDDEN"
