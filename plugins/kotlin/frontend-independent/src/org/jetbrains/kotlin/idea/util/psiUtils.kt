// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

fun KtElement.getElementTextInContext(): String {
    val context = parentOfType<KtImportDirective>()
        ?: parentOfType<KtPackageDirective>()
        ?: PsiTreeUtil.getParentOfType(this, KtDeclarationWithBody::class.java, KtClassOrObject::class.java)
        ?: PsiTreeUtil.getNonStrictParentOfType(this, KtProperty::class.java)
        ?: containingKtFile
    val builder = StringBuilder()
    context.accept(object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element === this@getElementTextInContext) builder.append("<$ELEMENT_TAG>")
            if (element is LeafPsiElement) {
                builder.append(element.text)
            } else {
                element.acceptChildren(this)
            }
            if (element === this@getElementTextInContext) builder.append("</$ELEMENT_TAG>")
        }
    })
    return builder.toString().trimIndent().trim()
}

private const val ELEMENT_TAG = "ELEMENT"

fun PsiElement.reformatted(canChangeWhiteSpacesOnly: Boolean = false): PsiElement = let {
    CodeStyleManager.getInstance(it.project).reformat(it, canChangeWhiteSpacesOnly)
}

fun PsiClass.classIdIfNonLocal(): ClassId? {
    if (this is KtLightClass) {
        return this.kotlinOrigin?.getClassId()
    }
    val packageName = (containingFile as? PsiJavaFile)?.packageName ?: return null
    val packageFqName = FqName(packageName)

    val classesNames = parentsOfType<PsiClass>().map { it.name }.toList().asReversed()
    if (classesNames.any { it == null }) return null
    return ClassId(packageFqName, FqName(classesNames.joinToString(separator = ".")), false)
}

fun KtExpression.resultingWhens(): List<KtWhenExpression> = when (this) {
    is KtWhenExpression -> listOf(this) + entries.map { it.expression?.resultingWhens() ?: listOf() }.flatten()
    is KtIfExpression -> (then?.resultingWhens() ?: listOf()) + (`else`?.resultingWhens() ?: listOf())
    is KtBinaryExpression -> (left?.resultingWhens() ?: listOf()) + (right?.resultingWhens() ?: listOf())
    is KtUnaryExpression -> this.baseExpression?.resultingWhens() ?: listOf()
    is KtBlockExpression -> statements.lastOrNull()?.resultingWhens() ?: listOf()
    else -> listOf()
}

fun generateWhenBranches(element: KtWhenExpression, missingCases: List<WhenMissingCase>) {
    val psiFactory = KtPsiFactory(element)
    val whenCloseBrace = element.closeBrace ?: run {
        val craftingMaterials = psiFactory.createExpression("when(1){}") as KtWhenExpression
        if (element.rightParenthesis == null) {
            element.addAfter(
                craftingMaterials.rightParenthesis!!,
                element.subjectExpression ?: throw AssertionError("caller should have checked the presence of subject expression.")
            )
        }
        if (element.openBrace == null) {
            element.addAfter(craftingMaterials.openBrace!!, element.rightParenthesis!!)
        }
        element.addAfter(craftingMaterials.closeBrace!!, element.entries.lastOrNull() ?: element.openBrace!!)
        element.closeBrace!!
    }
    val elseBranch = element.entries.find { it.isElse }
    (whenCloseBrace.prevSibling as? PsiWhiteSpace)?.replace(psiFactory.createNewLine())
    for (case in missingCases) {
        val branchConditionText = when (case) {
            WhenMissingCase.Unknown,
            WhenMissingCase.NullIsMissing,
            is WhenMissingCase.BooleanIsMissing,
            is WhenMissingCase.ConditionTypeIsExpect -> case.branchConditionText
            is WhenMissingCase.IsTypeCheckIsMissing ->
                if (case.isSingleton) {
                    ""
                } else {
                    "is "
                } + case.classId.asSingleFqName().render()
            is WhenMissingCase.EnumCheckIsMissing -> case.callableId.asSingleFqName().render()
        }
        val entry = psiFactory.createWhenEntry("$branchConditionText -> TODO()")
        if (elseBranch != null) {
            element.addBefore(entry, elseBranch)
        } else {
            element.addBefore(entry, whenCloseBrace)
        }
    }
}

/**
 * Consider a property initialization `val f: (Int) -> Unit = { println(it) }`. The type annotation `(Int) -> Unit` in this case is required
 * in order for the code to type check because otherwise the compiler cannot infer the type of `it`.
 */
tailrec fun KtCallableDeclaration.isExplicitTypeReferenceNeededForTypeInference(typeRef: KtTypeReference? = typeReference): Boolean {
    if (this !is KtDeclarationWithInitializer) return false
    val initializer = initializer
    if (initializer == null || typeRef == null) return false
    if (initializer !is KtLambdaExpression && initializer !is KtNamedFunction) return false
    val typeElement = typeRef.typeElement ?: return false
    if (typeRef.hasModifier(KtTokens.SUSPEND_KEYWORD)) return true
    return when (typeElement) {
        is KtFunctionType -> {
            if (typeElement.receiver != null) return true
            if (typeElement.parameters.isEmpty()) return false
            val valueParameters = when (initializer) {
                is KtLambdaExpression -> initializer.valueParameters
                is KtNamedFunction -> initializer.valueParameters
                else -> emptyList()
            }
            valueParameters.isEmpty() || valueParameters.any { it.typeReference == null }
        }
        is KtUserType -> {
            val typeAlias = typeElement.referenceExpression?.mainReference?.resolve() as? KtTypeAlias ?: return false
            return isExplicitTypeReferenceNeededForTypeInference(typeAlias.getTypeReference())
        }
        else -> false
    }
}

fun PsiClass.isSyntheticKotlinClass(): Boolean {
    if ('$' !in name!!) return false // optimization to not analyze annotations of all classes
    val metadata = modifierList?.findAnnotation(JvmAnnotationNames.METADATA_FQ_NAME.asString())
    return (metadata?.findAttributeValue(JvmAnnotationNames.KIND_FIELD_NAME) as? PsiLiteral)?.value ==
            KotlinClassHeader.Kind.SYNTHETIC_CLASS.id
}
