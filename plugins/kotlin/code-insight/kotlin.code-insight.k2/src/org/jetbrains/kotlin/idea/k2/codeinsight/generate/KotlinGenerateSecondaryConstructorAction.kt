// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject
import com.intellij.ide.util.MemberChooser
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.createInheritanceTypeSubstitutor
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.isVisibleInClass
import org.jetbrains.kotlin.analysis.api.components.namedClassSymbol
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinPsiElementMemberChooserObject
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.refactoring.addElement
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

class KotlinGenerateSecondaryConstructorAction : KotlinGenerateMemberActionBase<KotlinGenerateSecondaryConstructorAction.Info>() {
    class Info(
        val propertiesToInitialize: List<KotlinPsiElementMemberChooserObject>,
        val superConstructors: List<ClassMember>,
        val klass: KtClass
    )

    override fun isValidForClass(targetClass: KtClassOrObject): Boolean {
        return targetClass is KtClass
                && targetClass !is KtEnumEntry
                && !targetClass.isInterface()
                && !targetClass.isAnnotation()
                && !targetClass.hasExplicitPrimaryConstructor()
    }

    private fun shouldPreselect(element: PsiElement) = element is KtProperty && !element.isVar

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun chooseSuperConstructors(classSymbol: KaClassSymbol): List<ClassMember> {
        val superClassSymbol = getSuperClassSymbolNoAny(classSymbol) ?: return emptyList()
        val candidates = superClassSymbol.declaredMemberScope.constructors
            .filter { it.isVisibleInClass(classSymbol) }
            .mapNotNull { constructorSymbol ->
                constructorSymbol.psi?.let { KotlinPsiElementMemberChooserObject.getMemberChooserObject(it) } as? ClassMember
            }.toList()
        return candidates
    }

    context(_: KaSession)
    private fun getSuperClassSymbolNoAny(classSymbol: KaClassSymbol): KaClassSymbol? =
        classSymbol.superTypes.mapNotNull { it.symbol as? KaClassSymbol }.find { superClassSymbol ->
            superClassSymbol.classKind == KaClassKind.CLASS && superClassSymbol.classId != StandardClassIds.Any && superClassSymbol.classId != StandardClassIds.Enum
        }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KtProperty.isPropertyNotInitialized(): Boolean {
        // TODO: when KT-63221 is fixed use `diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)` instead
        return containingKtFile.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            .filter { it.psi == this@isPropertyNotInitialized }
            .any { it is KaFirDiagnostic.MustBeInitializedOrBeAbstract }
    }

    context(_: KaSession)
    private fun choosePropertiesToInitialize(klass: KtClassOrObject): List<KotlinPsiElementMemberChooserObject> {
        return klass.declarations
            .asSequence()
            .filterIsInstance<KtProperty>()
            .filter { it.isVar || it.isPropertyNotInitialized() }
            .map { KotlinPsiElementMemberChooserObject.getKotlinMemberChooserObject(it) }
            .toList()
    }

    override fun prepareMembersInfo(klass: KtClassOrObject, project: Project, editor: Editor): Info? {
        if (klass !is KtClass) return null
        val preInfo = analyzeInModalWindow(klass, KotlinBundle.message("fix.change.signature.prepare")) {
            val classSymbol = klass.symbol as? KaClassSymbol ?: return@analyzeInModalWindow null
            val superConstructors = chooseSuperConstructors(classSymbol)
            val propertiesToInitialize = choosePropertiesToInitialize(klass)
            return@analyzeInModalWindow Info(propertiesToInitialize, superConstructors, klass)
        } ?: return null

        val superConstructors = preInfo.superConstructors
        val chosenConstructors = if (isUnitTestMode() || superConstructors.size <= 1) {
            superConstructors
        }
        else {
            val chooser = MemberChooser(superConstructors.toTypedArray(), false, true, klass.project)
            chooser.title = JavaBundle.message("generate.constructor.super.constructor.chooser.title")
            chooser.setCopyJavadocVisible(false)
            chooser.show()

            chooser.selectedElements
        } ?: return null

        val properties = preInfo.propertiesToInitialize
        val chosenProperties = if (isUnitTestMode() || properties.isEmpty()) {
            properties
        }
        else {
            with(MemberChooser(properties.toTypedArray(), true, true, klass.project, false, null)) {
                title = KotlinBundle.message("action.generate.secondary.constructor.choose.properties")
                setCopyJavadocVisible(false)
                selectElements(properties.filter { shouldPreselect(it.element) }.toTypedArray())
                show()

                selectedElements
            }
        } ?: return null
        return Info(chosenProperties, chosenConstructors, preInfo.klass)
    }

    override fun generateMembers(project: Project, editor: Editor, info: Info): List<KtDeclaration> {
        val targetClass = info.klass

        fun Info.findAnchor(): PsiElement? {
            targetClass.declarations.lastIsInstanceOrNull<KtSecondaryConstructor>()?.let { return it }
            val lastPropertyToInitialize = propertiesToInitialize.lastOrNull()?.psiElement
            val declarationsAfter =
                lastPropertyToInitialize?.siblings()?.filterIsInstance<KtDeclaration>() ?: targetClass.declarations.asSequence()
            val firstNonProperty = declarationsAfter.firstOrNull { it !is KtProperty } ?: return null
            return firstNonProperty.siblings(forward = false).firstIsInstanceOrNull<KtProperty>() ?: targetClass.getOrCreateBody().lBrace
        }

        with(info) {
            val prototypes =
                analyzeInModalWindow(klass, KotlinBundle.message("fix.change.signature.prepare")) {
                    if (superConstructors.isNotEmpty()) {
                        superConstructors.mapNotNull { generateConstructor(klass, propertiesToInitialize.mapNotNull { it.psiElement as? KtProperty }, it) }
                    } else {
                        listOfNotNull(generateConstructor(klass, propertiesToInitialize.mapNotNull { it.psiElement as? KtProperty }, null))
                    }
                }


            if (prototypes.isEmpty()) {
                val errorText = KotlinBundle.message("action.generate.secondary.constructor.error.already.exists")
                CommonRefactoringUtil.showErrorHint(targetClass.project, editor, errorText, commandName, null)
                return emptyList()
            }

            var constructors: List<KtSecondaryConstructor>? = null
            project.executeWriteCommand(commandName) {
                constructors = insertMembersAfterAndReformat(editor, targetClass, prototypes, findAnchor())
            }
            return constructors ?: emptyList()
        }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun generateConstructor(
        klass: KtClass,
        propertiesToInitialize: List<KtProperty>,
        superConstructor: ClassMember?
    ): KtSecondaryConstructor? {

        val classSymbol = klass.symbol as? KaClassSymbol ?: return null

        val superConstructorPsi = (superConstructor as? PsiElementMemberChooserObject)?.psiElement
        val superConstructorSymbol = when (superConstructorPsi) {
            is KtConstructor<*> -> superConstructorPsi.symbol
            is PsiMethod -> superConstructorPsi.containingClass?.namedClassSymbol?.declaredMemberScope?.constructors?.find { it.psi == superConstructorPsi }
            else -> null
        }

        val constructorParamTypes = propertiesToInitialize.map { it.returnType } +
                                    (superConstructorSymbol?.valueParameters?.map { it.returnType } ?: emptyList())

        fun equalTypes(types1: Collection<KaType>, types2: Collection<KaType>): Boolean {
            return types1.size == types2.size && types1.zip(types2).all { it.first.semanticallyEquals(it.second) }
        }

        if (classSymbol.declaredMemberScope.constructors.any { constructorSymbol ->
                constructorSymbol.psi is KtConstructor<*> &&
                        equalTypes(constructorSymbol.valueParameters.map { it.returnType }, constructorParamTypes)
            }
        ) return null

        val psiFactory = KtPsiFactory(klass.project)

        val validator = CollectingNameValidator()

        val constructor = psiFactory.createSecondaryConstructor("constructor()")
        val parameterList = constructor.valueParameterList!!

        if (superConstructorSymbol != null) {
            val superClassSymbol = superConstructorSymbol.containingSymbol as? KaClassSymbol ?: return null
            val substitutor = createInheritanceTypeSubstitutor(classSymbol, superClassSymbol) ?: return null

            val delegationCallArguments = ArrayList<String>()
            for (parameter in superConstructorSymbol.valueParameters) {
                val isVararg = parameter.isVararg
                val paramName = suggestSafeNameByName(parameter.name.asString(), validator)
                val typeToUse = parameter.returnType
                val paramType = substitutor.substitute(typeToUse).render(position = Variance.OUT_VARIANCE)
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
                val propertyName = property.name ?: continue
                val paramName = suggestSafeNameByName(propertyName, validator)
                val paramType = property.returnType.render(position = Variance.IN_VARIANCE)

                parameterList.addParameter(psiFactory.createParameter("$paramName: $paramType"))
                body.addElement(psiFactory.createExpression("this.${propertyName.quoteIfNeeded()} = $paramName"), true)
            }

            constructor.add(body)
        }

        return constructor
    }

    private fun suggestSafeNameByName(originalName: String, validator: CollectingNameValidator): String =
        KotlinNameSuggester.suggestNameByName(originalName, validator).quoteIfNeeded()
}