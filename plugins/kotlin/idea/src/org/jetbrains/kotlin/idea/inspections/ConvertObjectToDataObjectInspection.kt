// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.asSafely
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.getCallChain
import org.jetbrains.kotlin.idea.base.psi.singleExpressionBody
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection.Companion.isSubclassOfStatelessSealed
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyEquals
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyHashCode
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyToString
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.isNothing

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.ConvertObjectToDataObject]
 */
class ConvertObjectToDataObjectInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (holder.file.languageVersionSettings.supportsFeature(LanguageFeature.DataObjects)) ObjectVisitor(holder)
        else PsiElementVisitor.EMPTY_VISITOR

    private class ObjectVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
        override fun visitObjectDeclaration(ktObject: KtObjectDeclaration) {
            if (ktObject.isData() || ktObject.isCompanion() || ktObject.isObjectLiteral()) return
            val fqName = lazy { ktObject.descriptor?.fqNameSafe ?: FqName.ROOT }
            val isSerializable = isSerializable(ktObject)
            val toString = ktObject.findToString()
            val candidate1 = toString == null && isSerializable
            val candidate2 = lazy { toString == null && ktObject.isSubclassOfStatelessSealed() }
            val candidate3 = lazy { toString != null && isCompatibleToString(ktObject, fqName, toString) }
            if ((candidate1 || candidate2.value || candidate3.value) &&
                isCompatibleHashCode(ktObject) &&
                isCompatibleEquals(ktObject, fqName) &&
                isCompatibleReadResolve(ktObject, fqName, isSerializable)
            ) {
                holder.registerProblem(
                    ktObject.getObjectKeyword() ?: return,
                    KotlinBundle.message(
                        when {
                            candidate1 -> "serializable.object.must.be.marked.with.data"
                            candidate2.value -> "inspection.message.sealed.object.can.be.converted.to.data.object"
                            else -> "inspection.message.object.with.manual.tostring.can.be.converted.to.data.object"
                        }
                    ),
                    ConvertToDataObjectQuickFix(isSerializable),
                )
            }
        }
    }
}

private fun isSerializable(ktObject: KtObjectDeclaration): Boolean =
    ktObject.resolveToDescriptorIfAny()
        ?.getAllSuperClassifiers()
        ?.any { it.fqNameUnsafe.asString() == "java.io.Serializable" } == true

private fun isCompatibleReadResolve(ktObject: KtObjectDeclaration, ktObjectFqn: Lazy<FqName>, isSerializable: Boolean): Boolean {
    if (!isSerializable) return true
    val readResolve = ktObject.findReadResolve() ?: return true
    if (!readResolve.isPrivate()) return false
    val fqn = readResolve.singleExpressionBody()
        ?.asSafely<KtNameReferenceExpression>()
        ?.resolveType()
        ?.fqName
    return ktObjectFqn.value == fqn
}

private fun isCompatibleToString(
    ktObject: KtObjectDeclaration,
    ktObjectFqn: Lazy<FqName>,
    toStringFunction: KtNamedFunction
): Boolean {
    val body = toStringFunction.singleExpressionBody() ?: return false
    if (body.text == "\"${ktObject.name}\"") return true
    val context = lazy { body.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL) }
    val callChain = body.tryUnwrapElvisOrDoubleBang(context).getCallChain()
    if (callChain.size !in 2..3) return false
    val fqn = callChain.firstOrNull()
        ?.asSafely<KtClassLiteralExpression>()
        ?.receiverExpression
        ?.asSafely<KtNameReferenceExpression>()
        ?.resolveType(context.value)
        ?.fqName
        ?: return false
    val methods = callChain.drop(1).map { it.asSafely<KtNameReferenceExpression>()?.text ?: return false }
    return fqn == ktObjectFqn.value && (methods == listOf("java", "simpleName") || methods == listOf("simpleName"))
}

private fun KtExpression.tryUnwrapElvisOrDoubleBang(context: Lazy<BindingContext>): KtExpression = when {
    this is KtPostfixExpression && operationToken == KtTokens.EXCLEXCL -> baseExpression
    this is KtBinaryExpression && operationToken == KtTokens.ELVIS -> left?.takeIf { right?.getType(context.value)?.isNothing() == true }
    else -> null
} ?: this

private fun isCompatibleEquals(ktObject: KtObjectDeclaration, ktObjectFqn: Lazy<FqName>): Boolean {
    val equals = ktObject.findEquals() ?: return true
    val isExpr = equals.singleExpressionBody().asSafely<KtIsExpression>() ?: return false
    val typeReference = isExpr.typeReference ?: return false
    return typeReference.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
        .get(BindingContext.TYPE, typeReference)?.fqName == ktObjectFqn.value
}

private fun isCompatibleHashCode(ktObject: KtObjectDeclaration): Boolean {
    val hashCode = ktObject.findHashCode() ?: return true
    return hashCode.singleExpressionBody() is KtConstantExpression
}

private class ConvertToDataObjectQuickFix(private val isSerializable: Boolean) : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.data.object")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val ktObject = descriptor.psiElement.parent.asSafely<KtObjectDeclaration>() ?: return
        ktObject.findToString()?.delete()
        ktObject.findEquals()?.delete()
        ktObject.findHashCode()?.delete()
        if (isSerializable) ktObject.findReadResolve()?.delete()
        if (ktObject.body?.declarations?.isEmpty() == true) ktObject.body?.delete()
        ktObject.addModifier(KtTokens.DATA_KEYWORD)
    }
}

private fun KtClassOrObject.findToString(): KtNamedFunction? = findMemberFunction(FunctionDescriptor::isAnyToString)
private fun KtClassOrObject.findEquals(): KtNamedFunction? = findMemberFunction(FunctionDescriptor::isAnyEquals)
private fun KtClassOrObject.findHashCode(): KtNamedFunction? = findMemberFunction(FunctionDescriptor::isAnyHashCode)
private fun KtClassOrObject.findReadResolve(): KtNamedFunction? =
    body?.functions?.singleOrNull { function ->
        function.name == "readResolve" && function.descriptor?.asSafely<FunctionDescriptor>()?.returnType?.isAnyOrNullableAny() == true
    }

private fun KtClassOrObject.findMemberFunction(predicate: (FunctionDescriptor) -> Boolean): KtNamedFunction? =
    body?.functions?.singleOrNull { function ->
        function.descriptor?.asSafely<FunctionDescriptor>()?.let(predicate) == true
    }
