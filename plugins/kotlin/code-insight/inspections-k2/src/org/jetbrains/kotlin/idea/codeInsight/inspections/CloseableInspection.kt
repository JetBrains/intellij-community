// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isCallingAnyOf
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Detects `Closeable`/`AutoCloseable` resources that are not properly closed via `.use { }`:
 *
 * **Case 1 – variable assignment**: warns when closeable is stored in a local variable
 * instead of being consumed by `use { }`.
 * ```kotlin
 * val stream = FileInputStream("file.txt")  // ← warned
 * ```
 *
 * **Case 2 – inline expression**: warns when closeable is consumed by a scope function
 * (`also`, `apply`) that does not close it.
 * ```kotlin
 * getStream().also { it.read() }  // ← warned; replace 'also' with 'use'
 * ```
 */
internal class CloseableInspection : KotlinApplicableInspectionBase<KtCallExpression, CloseableInspection.Context>() {
    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        getProblemDescription(context),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        *if (context == Context.NoCloseContext) emptyArray() else arrayOf(createQuickFix(context)),
    )

    internal sealed interface Context {
        data class VariableContext(val start: Int, val end: Int, val escaped: Map<String, PropertyUsage>) : Context
        data class ExpressionContext(val deep: Int) : Context

        object NoCloseContext : Context

    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
            super.visitCallExpression(expression)
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val type = element.expressionType ?: return null
        if (!type.isCloseable || element.isAlsoOrApply) {
            return null
        }

        return prepareContextLocal(element)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.prepareContextLocal(element: KtExpression, deep: Int = 0): Context? {
        val parent = element.parent
        if (parent is KtDotQualifiedExpression) {
            if (parent.callExpression == element || parent.callExpression?.isAlsoOrApply == true) {
                return prepareContextLocal(parent, deep + 1)
            }
            if (parent.callExpression?.isCallingAnyOf(StandardKotlinNames.use) == true) {
                return null
            }

            return Context.ExpressionContext(deep + 1)
        } else if (parent is KtProperty && parent.initializer?.expressionType?.isCloseable == true) {
            val scope = parent.parent

            if (scope is KtBlockExpression) {
                val parentName = parent.name ?: return null
                val statements = scope.statements
                var firstDeclaration = -1
                var closed = false
                val propertyIndex = statements.indexOf(parent)
                val declarations = hashMapOf(parentName to PropertyUsage(parent, propertyIndex, propertyIndex))
                val offset = propertyIndex + 1
                for (i in offset until statements.size) {
                    val statement = statements[i]
                    if (firstDeclaration == -1 && statement is KtDeclaration && statement !is KtProperty) {
                        firstDeclaration = i
                    }
                    if (statement.collectUsagesAndCheckClose(declarations, i, parent)) {
                        closed = true
                    }
                    if (statement is KtProperty) {
                        val name = statement.name ?: continue
                        declarations[name] = PropertyUsage(statement, i, i)
                    }
                }
                val mainProperty = declarations.remove(parentName)!!
                val escaped = declarations
                    .filter { it.value.index >= mainProperty.index && it.value.declare <= mainProperty.index}
                if (escaped.size < 2 && firstDeclaration == -1) {
                    return Context.VariableContext(propertyIndex, mainProperty.index, escaped)
                } else if (!closed) {
                    return Context.NoCloseContext
                }
            }
        } else if (parent is KtBlockExpression) {
            val statements = parent.statements
            if (statements.lastOrNull() == element &&
                parent.expressionType?.isCloseable == true &&
                parent.parent !is KtNamedFunction) {
                return null
            }
            return Context.ExpressionContext(deep)
        }

        return null
    }

    private fun getProblemDescription(context: Context): String = when (context) {
        is Context.VariableContext ->
            KotlinBundle.message("inspection.closeable.variable.description")

        is Context.ExpressionContext ->
            KotlinBundle.message("inspection.closeable.expression.description")

        is Context.NoCloseContext ->
            KotlinBundle.message("inspection.closeable.no.close.description")
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
        val parent = element.parent
        if (parent is KtDotQualifiedExpression && parent.selectorExpression == element) {
            val calleeExpr = element.calleeExpression
                ?: return listOf(TextRange(0, element.textLength))
            return listOf(
                TextRange(
                    calleeExpr.startOffset - element.startOffset,
                    calleeExpr.endOffset - element.startOffset,
                )
            )
        }
        return listOf(TextRange(0, element.textLength))
    }

    private fun createQuickFix(
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.closeable.variable.fix.name")

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val psiFactory = KtPsiFactory(project)
            when (context) {
                is Context.VariableContext -> {
                    val property = element.getStrictParentOfType<KtProperty>() ?: return
                    val varName = property.name ?: return
                    val scope = property.parent as? KtBlockExpression ?: return
                    val statements = scope.statements
                    val escapedProperties = context.escaped.values.map { it.property }.toSet()
                    val statementsAfter = statements.subList(context.start + 1, context.end + 1).toList()
                    val bodyStatements = statementsAfter.filterNot { stmt ->
                        if (stmt in escapedProperties) return@filterNot true
                        val dotExpr = stmt as? KtDotQualifiedExpression ?: return@filterNot false
                        val receiver = dotExpr.receiverExpression as? KtNameReferenceExpression ?: return@filterNot false
                        val call = dotExpr.selectorExpression as? KtCallExpression ?: return@filterNot false
                        receiver.text == varName && call.calleeExpression?.text == "close" && call.valueArguments.isEmpty()
                    }
                    val receiverText = property.initializer?.text ?: return
                    val newNode: PsiElement = when (context.escaped.size) {
                        0 -> {
                            val bodyText = bodyStatements.joinToString("\n") { it.text }
                            psiFactory.createExpression("$receiverText.use { $varName ->\n$bodyText\n}")
                        }

                        1 -> {
                            val escapedProp = context.escaped.values.first().property
                            val name = escapedProp.name ?: return
                            val lastStatement = bodyStatements.lastOrNull()
                            val bodyText = if (lastStatement is KtProperty && lastStatement.name == escapedProp.name) {
                                val initText = escapedProp.initializer?.text ?: return
                                (bodyStatements.dropLast(1).map { it.text } + initText)
                            } else {
                                (bodyStatements.map { it.text } + name)
                            }.joinToString("\n")
                            val keyword = escapedProp.valOrVarKeyword.text
                            psiFactory.createProperty("$keyword $name = $receiverText.use { $varName ->\n$bodyText\n}")
                        }

                        else -> error("Unexpected number of escaped variables: ${context.escaped.size}")
                    }
                    for (stmt in statementsAfter) {
                        stmt.delete()
                    }
                    property.replace(newNode)
                }

                is Context.ExpressionContext -> {
                    var e: PsiElement = element
                    repeat(context.deep) {
                        e = e.parent
                    }
                    val expression1 = e as? KtDotQualifiedExpression
                    val newElement = if (expression1 != null && expression1.callExpression != element) {
                        expression1.receiverExpression.text?.let {
                            psiFactory.createExpression("$it.use { it${e.text.substring(it.length)}}")
                        }
                    } else {
                        null
                    } ?: psiFactory.createExpression("${e.text}.use { }")
                    e.replace(newElement)
                }

                is Context.NoCloseContext -> {}
            }
        }
    }
}

context(_: KaSession)
private val KtCallExpression.isAlsoOrApply: Boolean
    get() =
        isCallingAnyOf(StandardKotlinNames.also, StandardKotlinNames.apply)

private val CLOSEABLE_ID = ClassId.fromString("java.io/Closeable")
private val AUTO_CLOSEABLE_ID = ClassId.fromString("kotlin/AutoCloseable")
private val JAVA_AUTO_CLOSEABLE_ID = ClassId.fromString("java.lang/AutoCloseable")

private val CLOSEABLE_CLOSE_ID = CallableId(CLOSEABLE_ID, Name.identifier("close"))
private val AUTO_CLOSEABLE_CLOSE_ID =
    CallableId(AUTO_CLOSEABLE_ID, Name.identifier("close"))
private val JAVA_AUTO_CLOSEABLE_CLOSE_ID =
    CallableId(JAVA_AUTO_CLOSEABLE_ID, Name.identifier("close"))

context(_: KaSession)
private val KaType.isCloseable: Boolean
    get() =
        isSubtypeOf(CLOSEABLE_ID) || isSubtypeOf(AUTO_CLOSEABLE_ID)

context(_: KaSession)
private fun KtCallExpression.isCloseableClose(): Boolean {
    if (calleeExpression?.text != "close" || valueArguments.isNotEmpty()) return false
    val symbol = resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return false
    return symbol.allOverriddenSymbolsWithSelf.any { sym ->
        val id = sym.callableId
        id == CLOSEABLE_CLOSE_ID || id == AUTO_CLOSEABLE_CLOSE_ID || id == JAVA_AUTO_CLOSEABLE_CLOSE_ID
    }
}

context(_: KaSession)
private fun KtExpression.collectUsagesAndCheckClose(map: MutableMap<String, PropertyUsage>, index: Int, property: KtProperty): Boolean {
    var result = false
    this.accept(object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            super.visitReferenceExpression(expression)
            val property = map[expression.text]
            if (property != null && expression.mainReference.isReferenceTo(property.property)) {
                property.index = index
            }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            val callee = expression.callExpression
            val receiver = expression.receiverExpression
            if (receiver.mainReference?.isReferenceTo(property) == true &&
                callee?.isCloseableClose() == true
            ) {
                result = true
            }
            super.visitDotQualifiedExpression(expression)
        }
    })

    return result
}

internal data class PropertyUsage(val property: KtProperty, val declare: Int, var index: Int)
