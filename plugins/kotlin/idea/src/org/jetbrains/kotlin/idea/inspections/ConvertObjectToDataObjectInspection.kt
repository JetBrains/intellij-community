// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.asSafely
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
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
import org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection.Companion.asKtClass
import org.jetbrains.kotlin.idea.inspections.VirtualFunction.*
import org.jetbrains.kotlin.idea.inspections.VirtualFunction.Function
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.*
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
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
            val isSerializableCase = toString == TrivialSuper && isSerializable
            val isSealedSubClassCase by lazy { toString == TrivialSuper && ktObject.isSubclassOfSealed() }
            val isToStringCase by lazy { toString is Function && isCompatibleToString(ktObject, fqName, toString.function) }
            if ((isSerializableCase || isSealedSubClassCase || isToStringCase) &&
                isCompatibleHashCode(ktObject) &&
                isCompatibleEquals(ktObject, fqName) &&
                isCompatibleReadResolve(ktObject, fqName, isSerializable)
            ) {
                holder.registerProblem(
                    ktObject.getObjectKeyword() ?: return,
                    KotlinBundle.message(
                        when {
                            isSerializableCase -> "serializable.object.must.be.marked.with.data"
                            isSealedSubClassCase -> "inspection.message.sealed.object.can.be.converted.to.data.object"
                            else -> "inspection.message.object.with.manual.tostring.can.be.converted.to.data.object"
                        }
                    ),
                    ConvertToDataObjectQuickFix(isSerializable),
                )
            }
        }
    }
}

private fun KtObjectDeclaration.isSubclassOfSealed(): Boolean =
    superTypeListEntries.asSequence().mapNotNull { it.asKtClass() }.any { it.isSealed() }

private fun isSerializable(ktObject: KtObjectDeclaration): Boolean =
    ktObject.resolveToDescriptorIfAny()
        ?.getAllSuperClassifiers()
        ?.any { it.fqNameSafe == FqName("java.io.Serializable") } == true

private fun isCompatibleReadResolve(ktObject: KtObjectDeclaration, ktObjectFqn: Lazy<FqName>, isSerializable: Boolean): Boolean =
    !isSerializable || when (val readResolve = ktObject.findReadResolve()) {
        is Function -> ktObjectFqn.value == readResolve.function.takeIf(KtNamedFunction::isPrivate)?.singleExpressionBody()
            ?.asSafely<KtNameReferenceExpression>()
            ?.resolveType()
            ?.fqName
        NonTrivialSuper -> false
        TrivialSuper -> true
    }

private fun isCompatibleToString(
    ktObject: KtObjectDeclaration,
    ktObjectFqn: Lazy<FqName>,
    toStringFunction: KtNamedFunction
): Boolean {
    val body = toStringFunction.singleExpressionBody() ?: return false
    if ((body as? KtStringTemplateExpression)?.entries?.singleOrNull()?.text == ktObject.name) return true
    val context = lazy { body.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL) }
    val callChain = body.tryUnwrapElvisOrDoubleBang(context).getCallChain().takeIf { it.size in 2..3 } ?: return false
    val classLiteralReceiver = callChain.firstOrNull()?.asSafely<KtClassLiteralExpression>()?.receiverExpression
    val fqn = classLiteralReceiver?.asSafely<KtNameReferenceExpression>()?.resolveType(context.value)?.fqName
    val methods = callChain.drop(1).map { it.asSafely<KtNameReferenceExpression>()?.text ?: return false }
    return (classLiteralReceiver is KtThisExpression || fqn == ktObjectFqn.value) &&
            (methods == listOf("java", "simpleName") || methods == listOf("simpleName")) ||
            callChain.firstOrNull() is KtThisExpression && methods == listOf("javaClass", "simpleName")
}

private fun KtExpression.tryUnwrapElvisOrDoubleBang(context: Lazy<BindingContext>): KtExpression = when {
    this is KtPostfixExpression && operationToken == KtTokens.EXCLEXCL -> baseExpression
    this is KtBinaryExpression && operationToken == KtTokens.ELVIS -> left?.takeIf { right?.getType(context.value)?.isNothing() == true }
    else -> null
} ?: this

private fun isCompatibleEquals(ktObject: KtObjectDeclaration, ktObjectFqn: Lazy<FqName>): Boolean =
    when (val equals = ktObject.findEquals()) {
        is Function -> ktObjectFqn.value == equals.function.singleExpressionBody()
            ?.asSafely<KtIsExpression>()?.typeReference?.let { typeReference ->
                typeReference.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)[BindingContext.TYPE, typeReference]?.fqName
            }
        NonTrivialSuper -> false
        TrivialSuper -> true
    }

private fun isCompatibleHashCode(ktObject: KtObjectDeclaration): Boolean =
    when (val hashCode = ktObject.findHashCode()) {
        is Function -> hashCode.function.singleExpressionBody() is KtConstantExpression
        NonTrivialSuper -> false
        TrivialSuper -> true
    }

private class ConvertToDataObjectQuickFix(private val isSerializable: Boolean) : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.data.object")

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val ktObject = descriptor.psiElement.parent.asSafely<KtObjectDeclaration>() ?: return
        val functions = ActionUtil.underModalProgress(project, KotlinBundle.message("analyzing.members"), Computable {
            listOfNotNull(
                ktObject.findToString().function,
                ktObject.findEquals().function,
                ktObject.findHashCode().function,
                if (isSerializable) ktObject.findReadResolve().function else null,
            )
        })
        runWriteAction {
            functions.forEach { it.delete() }
            if (ktObject.body?.declarations?.isEmpty() == true) ktObject.body?.delete()
            ktObject.addModifier(KtTokens.DATA_KEYWORD)
        }
    }

    private val VirtualFunction.function: KtNamedFunction?
        get() = when (this) {
            is Function -> function
            is NonTrivialSuper -> null
            is TrivialSuper -> null
        }
}

private fun KtObjectDeclaration.findToString() = findMemberFunction(TO_STRING, KOTLIN_TO_STRING_FQN, FunctionDescriptor::isAnyToString)
private fun KtObjectDeclaration.findEquals() = findMemberFunction(EQUALS, KOTLIN_ANY_EQUALS_FQN, FunctionDescriptor::isAnyEquals)
private fun KtObjectDeclaration.findHashCode() = findMemberFunction(HASH_CODE, KOTLIN_ANY_HASH_CODE_FQN, FunctionDescriptor::isAnyHashCode)
private fun KtObjectDeclaration.findReadResolve(): VirtualFunction = findMemberFunction("readResolve", trivialSuperFqn = null) {
    it.valueParameters.isEmpty() && it.returnType?.isAnyOrNullableAny() == true
}

private fun KtObjectDeclaration.findMemberFunction(
    name: String,
    trivialSuperFqn: String?,
    predicate: (FunctionDescriptor) -> Boolean
): VirtualFunction =
    if (trivialSuperFqn?.let { FqName(it) } != (descriptor as? ClassDescriptor)?.unsubstitutedMemberScope
            ?.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) { it == Name.identifier(name) }
            ?.asSequence()
            ?.filterIsInstance<FunctionDescriptor>()
            ?.singleOrNull(predicate)
            ?.findClosestNotFakeSuper()
            ?.fqNameSafe) NonTrivialSuper
    else body?.functions
        ?.singleOrNull { function ->
            function.name == name && function.descriptor?.asSafely<FunctionDescriptor>()?.let(predicate) == true
        }
        ?.let { Function(it) }
        ?: TrivialSuper

private fun FunctionDescriptor.findClosestNotFakeSuper(): FunctionDescriptor? =
    generateSequence(this) { it.overriddenDescriptors.singleOrNull() }
        .drop(1)
        .firstOrNull { it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE }

private sealed interface VirtualFunction {
    class Function(val function: KtNamedFunction) : VirtualFunction
    object NonTrivialSuper : VirtualFunction
    object TrivialSuper : VirtualFunction
}
