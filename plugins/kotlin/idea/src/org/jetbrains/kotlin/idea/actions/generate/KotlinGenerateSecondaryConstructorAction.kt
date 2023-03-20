// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.generate

import com.intellij.ide.util.MemberChooser
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.DescriptorMemberChooserObject
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.orEmpty
import org.jetbrains.kotlin.idea.util.toSubstitutor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

class KotlinGenerateSecondaryConstructorAction : KotlinGenerateMemberActionBase<KotlinGenerateSecondaryConstructorAction.Info>() {
    class Info(
        val propertiesToInitialize: List<PropertyDescriptor>,
        val superConstructors: List<ConstructorDescriptor>,
        val classDescriptor: ClassDescriptor
    )

    override fun isValidForClass(targetClass: KtClassOrObject): Boolean {
        return targetClass is KtClass
                && targetClass !is KtEnumEntry
                && !targetClass.isInterface()
                && !targetClass.isAnnotation()
                && !targetClass.hasExplicitPrimaryConstructor()
    }

    private fun shouldPreselect(element: PsiElement) = element is KtProperty && !element.isVar

    private fun chooseSuperConstructors(klass: KtClassOrObject, classDescriptor: ClassDescriptor): List<DescriptorMemberChooserObject> {
        val project = klass.project
        val superClassDescriptor = classDescriptor.getSuperClassNotAny() ?: return emptyList()
        val candidates = superClassDescriptor.constructors
            .filter { it.isVisible(classDescriptor, klass.getResolutionFacade().languageVersionSettings) }
            .map { DescriptorMemberChooserObject(DescriptorToSourceUtilsIde.getAnyDeclaration(project, it) ?: klass, it) }
        if (isUnitTestMode() || candidates.size <= 1) return candidates

        return with(MemberChooser(candidates.toTypedArray(), false, true, klass.project)) {
            title = JavaBundle.message("generate.constructor.super.constructor.chooser.title")
            setCopyJavadocVisible(false)
            show()

            selectedElements ?: emptyList()
        }
    }

    private fun choosePropertiesToInitialize(klass: KtClassOrObject, context: BindingContext): List<DescriptorMemberChooserObject> {
        val candidates = klass.declarations
            .asSequence()
            .filterIsInstance<KtProperty>()
            .filter { it.isVar || context.diagnostics.forElement(it).any { it.factory in Errors.MUST_BE_INITIALIZED_DIAGNOSTICS } }
            .map { context.get(BindingContext.VARIABLE, it) as PropertyDescriptor }
            .map { DescriptorMemberChooserObject(it.source.getPsi()!!, it) }
            .toList()
        if (isUnitTestMode() || candidates.isEmpty()) return candidates

        return with(MemberChooser(candidates.toTypedArray(), true, true, klass.project, false, null)) {
            title = KotlinBundle.message("action.generate.secondary.constructor.choose.properties")
            setCopyJavadocVisible(false)
            selectElements(candidates.filter { shouldPreselect(it.element) }.toTypedArray())
            show()

            selectedElements ?: emptyList()
        }
    }

    override fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor?): Info? {
        val context = klass.analyzeWithContent()
        val classDescriptor = context.get(BindingContext.CLASS, klass) ?: return null
        val superConstructors = chooseSuperConstructors(klass, classDescriptor).map { it.descriptor as ConstructorDescriptor }
        val propertiesToInitialize = choosePropertiesToInitialize(klass, context).map { it.descriptor as PropertyDescriptor }
        return Info(propertiesToInitialize, superConstructors, classDescriptor)
    }

    override fun generateMembers(project: Project, editor: Editor?, info: Info): List<KtDeclaration> {
        val targetClass = info.classDescriptor.source.getPsi() as? KtClass ?: return emptyList()

        fun Info.findAnchor(): PsiElement? {
            targetClass.declarations.lastIsInstanceOrNull<KtSecondaryConstructor>()?.let { return it }
            val lastPropertyToInitialize = propertiesToInitialize.lastOrNull()?.source?.getPsi()
            val declarationsAfter =
                lastPropertyToInitialize?.siblings()?.filterIsInstance<KtDeclaration>() ?: targetClass.declarations.asSequence()
            val firstNonProperty = declarationsAfter.firstOrNull { it !is KtProperty } ?: return null
            return firstNonProperty.siblings(forward = false).firstIsInstanceOrNull<KtProperty>() ?: targetClass.getOrCreateBody().lBrace
        }

        return with(info) {
            val prototypes = if (superConstructors.isNotEmpty()) {
                superConstructors.mapNotNull { generateConstructor(classDescriptor, propertiesToInitialize, it) }
            } else {
                listOfNotNull(generateConstructor(classDescriptor, propertiesToInitialize, null))
            }

            if (prototypes.isEmpty()) {
                val errorText = KotlinBundle.message("action.generate.secondary.constructor.error.already.exists")
                CommonRefactoringUtil.showErrorHint(targetClass.project, editor, errorText, commandName, null)
                return emptyList()
            }

            insertMembersAfterAndReformat(editor, targetClass, prototypes, findAnchor())
        }
    }

    private fun generateConstructor(
        classDescriptor: ClassDescriptor,
        propertiesToInitialize: List<PropertyDescriptor>,
        superConstructor: ConstructorDescriptor?
    ): KtSecondaryConstructor? {
        fun equalTypes(types1: Collection<KotlinType>, types2: Collection<KotlinType>): Boolean {
            return types1.size == types2.size && (types1.zip(types2)).all { KotlinTypeChecker.DEFAULT.equalTypes(it.first, it.second) }
        }

        val constructorParamTypes = propertiesToInitialize.map { it.type } +
                (superConstructor?.valueParameters?.map { it.varargElementType ?: it.type } ?: emptyList())

        if (classDescriptor.constructors.any { descriptor ->
                descriptor.source.getPsi() is KtConstructor<*> &&
                        equalTypes(descriptor.valueParameters.map { it.varargElementType ?: it.type }, constructorParamTypes)
            }
        ) return null

        val targetClass = classDescriptor.source.getPsi() as KtClass
        val psiFactory = KtPsiFactory(targetClass.project)

        val validator = CollectingNameValidator()

        val constructor = psiFactory.createSecondaryConstructor("constructor()")
        val parameterList = constructor.valueParameterList!!

        if (superConstructor != null) {
            val superClassType = superConstructor.containingDeclaration.defaultType
            val substitutor = getTypeSubstitution(superClassType, classDescriptor.defaultType)?.toSubstitutor().orEmpty()

            val delegationCallArguments = ArrayList<String>()
            for (parameter in superConstructor.valueParameters) {
                val isVararg = parameter.varargElementType != null
                val paramName = suggestSafeNameByName(parameter.name.asString(), validator)
                val typeToUse = parameter.varargElementType ?: parameter.type
                val paramType = IdeDescriptorRenderers.SOURCE_CODE.renderType(
                    substitutor.substitute(typeToUse, Variance.INVARIANT) ?: classDescriptor.builtIns.anyType
                )
                val modifiers = if (isVararg) "vararg " else ""

                parameterList.addParameter(psiFactory.createParameter("$modifiers$paramName: $paramType"))
                delegationCallArguments.add(if (isVararg) "*$paramName" else paramName)
            }

            val delegationCall =
                psiFactory.creareDelegatedSuperTypeEntry(delegationCallArguments.joinToString(prefix = "super(", postfix = ")"))
            constructor.replaceImplicitDelegationCallWithExplicit(false).replace(delegationCall)
        }

        if (propertiesToInitialize.isNotEmpty()) {
            val body = psiFactory.createEmptyBody()
            for (property in propertiesToInitialize) {
                val propertyName = property.name.asString()
                val paramName = suggestSafeNameByName(propertyName, validator)
                val paramType = IdeDescriptorRenderers.SOURCE_CODE.renderType(property.type)

                parameterList.addParameter(psiFactory.createParameter("$paramName: $paramType"))
                body.appendElement(psiFactory.createExpression("this.${propertyName.quoteIfNeeded()} = $paramName"), true)
            }

            constructor.add(body)
        }

        return constructor
    }

    private fun suggestSafeNameByName(originalName: String, validator: CollectingNameValidator): String =
        Fe10KotlinNameSuggester.suggestNameByName(originalName, validator).quoteIfNeeded()
}
