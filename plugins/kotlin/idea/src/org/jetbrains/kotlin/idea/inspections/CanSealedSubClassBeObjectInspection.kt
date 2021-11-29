// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.getModalityFromDescriptor
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.quickfix.sealedSubClassToObject.ConvertSealedSubClassToObjectFix
import org.jetbrains.kotlin.idea.quickfix.sealedSubClassToObject.GenerateIdentityEqualsFix
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.isEffectivelyActual
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.util.OperatorNameConventions

class CanSealedSubClassBeObjectInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        fun reportPossibleObject(klass: KtClass) {
            val keyword = klass.getClassOrInterfaceKeyword() ?: return
            val isExpectClass = klass.isExpectDeclaration()
            val fixes = listOfNotNull(
                createFixIfPossible(!isExpectClass && !klass.isEffectivelyActual(), ::ConvertSealedSubClassToObjectFix),
                createFixIfPossible(!isExpectClass && klass.module?.platform?.isJvm() == true, ::GenerateIdentityEqualsFix),
            ).toTypedArray()

            holder.registerProblem(
                keyword,
                KotlinBundle.message("sealed.sub.class.has.no.state.and.no.overridden.equals"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *fixes,
            )
        }

        return object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                if (klass.matchesCanBeObjectCriteria()) {
                    reportPossibleObject(klass)
                }
            }
        }
    }

    companion object {
        private val EQUALS = OperatorNameConventions.EQUALS.asString()

        private const val HASH_CODE = "hashCode"

        private val CLASS_MODIFIERS = listOf(
            KtTokens.ANNOTATION_KEYWORD,
            KtTokens.DATA_KEYWORD,
            KtTokens.ENUM_KEYWORD,
            KtTokens.INNER_KEYWORD,
            KtTokens.SEALED_KEYWORD,
        )

        private fun KtClass.matchesCanBeObjectCriteria(): Boolean {
            return isSubclassOfStatelessSealed()
                    && withEmptyConstructors()
                    && hasNoClassModifiers()
                    && isFinal()
                    && typeParameters.isEmpty()
                    && hasNoInnerClass()
                    && companionObjects.isEmpty()
                    && hasNoStateOrEquals()
        }

        private fun KtClass.isSubclassOfStatelessSealed(): Boolean {
            fun KtSuperTypeListEntry.asKtClass(): KtClass? = typeAsUserType?.referenceExpression?.mainReference?.resolve() as? KtClass
            return superTypeListEntries.asSequence().mapNotNull { it.asKtClass() }.any {
                it.isSealed() && it.hasNoStateOrEquals() && it.baseClassHasNoStateOrEquals()
            }
        }

        private fun KtClass.withEmptyConstructors(): Boolean =
            primaryConstructorParameters.isEmpty() && secondaryConstructors.all { it.valueParameters.isEmpty() }

        private fun KtClass.hasNoClassModifiers(): Boolean {
            val modifierList = modifierList ?: return true
            return CLASS_MODIFIERS.none { modifierList.hasModifier(it) }
        }

        private fun KtClass.isFinal(): Boolean = getModalityFromDescriptor() == KtTokens.FINAL_KEYWORD

        private tailrec fun KtClass.baseClassHasNoStateOrEquals(): Boolean {
            val descriptor = resolveToDescriptorIfAny() ?: return false
            val superDescriptor = descriptor.getSuperClassNotAny() ?: return true // No super class -- no state
            val superClass = DescriptorToSourceUtils.descriptorToDeclaration(superDescriptor) as? KtClass ?: return false
            if (!superClass.hasNoStateOrEquals()) return false
            return superClass.baseClassHasNoStateOrEquals()
        }

        private fun KtClass.hasNoStateOrEquals(): Boolean {
            if (primaryConstructor?.valueParameters?.isNotEmpty() == true) return false
            val body = body
            return body == null || run {
                val declarations = body.declarations
                declarations.asSequence().filterIsInstance<KtProperty>().none { property ->
                    // Simplified "backing field required"
                    when {
                        property.isAbstract() -> false
                        property.initializer != null -> true
                        property.delegate != null -> false
                        !property.isVar -> property.getter == null
                        else -> property.getter == null || property.setter == null
                    }
                } && declarations.asSequence().filterIsInstance<KtNamedFunction>().none { function ->
                    val name = function.name
                    val valueParameters = function.valueParameters
                    val noTypeParameters = function.typeParameters.isEmpty()
                    noTypeParameters && (name == EQUALS && valueParameters.size == 1 || name == HASH_CODE && valueParameters.isEmpty())
                }
            }
        }

        private fun KtClass.hasNoInnerClass(): Boolean {
            val internalClasses = body
                ?.declarations
                ?.filterIsInstance<KtClass>() ?: return true

            return internalClasses.none { klass -> klass.isInner() }
        }
    }
}

private fun <T : LocalQuickFix> createFixIfPossible(
    flag: Boolean,
    quickFixFactory: () -> T,
): T? = quickFixFactory.takeIf { flag }?.invoke()
