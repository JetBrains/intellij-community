// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.Language
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgressIfEdt
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createDeclarationByPattern
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class CanSealedSubClassBeObjectInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        fun reportPossibleObject(klass: KtClass) {
            val keyword = klass.getClassOrInterfaceKeyword() ?: return
            val isExpectClass = klass.isExpectDeclaration()
            val fixes = listOfNotNull(
                if (!isExpectClass && !klass.isEffectivelyActual()) ConvertSealedSubClassToObjectFix() else null,
                if (!isExpectClass && !klass.isData() && klass.module?.platform?.isJvm() == true) GenerateIdentityEqualsFix() else null,
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
}

private val EQUALS: String = OperatorNameConventions.EQUALS.asString()
private const val HASH_CODE: String = "hashCode"

private fun getExclusivelyClassModifiers(languageVersionSettings: LanguageVersionSettings): List<KtModifierKeywordToken> = listOfNotNull(
    KtTokens.ANNOTATION_KEYWORD,
    KtTokens.DATA_KEYWORD.takeUnless { languageVersionSettings.supportsFeature(LanguageFeature.DataObjects) },
    KtTokens.ENUM_KEYWORD,
    KtTokens.INNER_KEYWORD,
    KtTokens.SEALED_KEYWORD,
)

private fun KtClass.matchesCanBeObjectCriteria(): Boolean {
    return isSubclassOfStatelessSealed()
            && withEmptyConstructors()
            && hasNoExclusivelyClassModifiers()
            && isFinal()
            && typeParameters.isEmpty()
            && hasNoInnerClass()
            && companionObjects.isEmpty()
            && hasNoStateOrEquals()
}

private fun KtClassOrObject.isSubclassOfStatelessSealed(): Boolean =
    superTypeListEntries.asSequence().mapNotNull { it.resolveToKtClass() }.any {
        it.isSealed() && it.hasNoStateOrEquals() && it.baseClassHasNoStateOrEquals()
    }

private fun KtClass.withEmptyConstructors(): Boolean =
    primaryConstructorParameters.isEmpty() && secondaryConstructors.all { it.valueParameters.isEmpty() }

private fun KtClass.hasNoExclusivelyClassModifiers(): Boolean {
    val modifierList = modifierList ?: return true
    return getExclusivelyClassModifiers(languageVersionSettings).none { modifierList.hasModifier(it) }
}

private fun KtClass.isFinal(): Boolean = analyze(this) {
    (symbol as? KaNamedClassSymbol)?.modality == KaSymbolModality.FINAL
}

private tailrec fun KtClass.baseClassHasNoStateOrEquals(): Boolean {
    val superClass = directSuperClass() ?: return true
    return superClass.hasNoStateOrEquals() && superClass.baseClassHasNoStateOrEquals()
}

private fun KtClass.hasNoStateOrEquals(): Boolean {
    if (primaryConstructor?.valueParameters?.isNotEmpty() == true) return false
    val body = body
    return body == null || run {
        val declarations = body.declarations
        declarations.asSequence().filterIsInstance<KtProperty>().none { property ->
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

private fun KtSuperTypeListEntry.resolveToKtClass(): KtClass? =
    when (val resolved = typeAsUserType?.referenceExpression?.mainReference?.resolve()) {
        is KtConstructor<*> -> resolved.containingClass()
        is KtClass -> resolved
        else -> null
    }

private fun KtClass.directSuperClass(): KtClass? {
    return superTypeListEntries.firstNotNullOfOrNull { it.resolveToKtClass() }
}

private class ConvertSealedSubClassToObjectFix : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("convert.sealed.subclass.to.object.fix.family.name")

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val klass = descriptor.psiElement.getParentOfType<KtClass>(false) ?: return
        val classPointer: SmartPsiElementPointer<KtClass> = SmartPointerManager.createPointer(klass)

        changeInstances(classPointer)
        changeDeclaration(classPointer)
    }

    private fun changeDeclaration(pointer: SmartPsiElementPointer<KtClass>) {
        runWriteAction {
            val klass = pointer.element ?: return@runWriteAction
            val psiFactory = KtPsiFactory(klass.project)

            klass.changeToObject(psiFactory)
            klass.transformToObject(psiFactory)
        }
    }

    private fun KtClass.changeToObject(factory: KtPsiFactory) {
        ensureSuperClassCall(factory)
        secondaryConstructors.toList().forEach { constructor ->
            constructor.toInitializer(factory)?.let(::addDeclaration)
            constructor.delete()
        }
        primaryConstructor?.delete()
        getClassOrInterfaceKeyword()?.replace(
            if (!isData() && languageVersionSettings.supportsFeature(LanguageFeature.DataObjects)) {
                factory.createDeclarationByPattern("${KtTokens.DATA_KEYWORD.value} ${KtTokens.OBJECT_KEYWORD.value}")
            }
            else {
                factory.createExpression(KtTokens.OBJECT_KEYWORD.value)
            }
        )
    }

    private fun KtClass.ensureSuperClassCall(factory: KtPsiFactory) {
        if (secondaryConstructors.isEmpty()) return

        val superClassEntry = superTypeListEntries.firstOrNull { it !is KtSuperTypeCallEntry } ?: return

        superClassEntry.replace(factory.createSuperTypeCallEntry("${superClassEntry.text}()"))
    }

    private fun KtClass.transformToObject(factory: KtPsiFactory) {
        replace(factory.createObject(text))
    }

    private fun changeInstances(pointer: SmartPsiElementPointer<KtClass>) {
        mapReferencesByLanguage(pointer).apply {
            runWriteAction {
                val klass = pointer.element ?: return@runWriteAction
                replaceKotlin(klass)
                replaceJava(klass)
            }
        }
    }

    private fun mapReferencesByLanguage(pointer: SmartPsiElementPointer<KtClass>): Map<Language?, List<PsiElement>> =
        pointer.project.runSynchronouslyWithProgressIfEdt(KotlinBundle.message("progress.looking.up.sealed.subclass.usage"), true) {
            pointer.element?.let { klass ->
                ReferencesSearch.search(klass).findAll().groupBy({ it.element.language }, { it.element.parent })
            } ?: emptyMap()
        } ?: emptyMap()

    private fun Map<Language?, List<PsiElement>>.replaceKotlin(klass: KtClass) {
        val kotlinReferences = this[KOTLIN_LANGUAGE] ?: return
        val singletonCall = KtPsiFactory(klass.project).createExpression(klass.name ?: return)

        kotlinReferences.filter { it.node.elementType == KtNodeTypes.CALL_EXPRESSION }
            .forEach { it.replace(singletonCall) }
    }

    private fun Map<Language?, List<PsiElement>>.replaceJava(klass: KtClass) {
        val javaReferences = this[JAVA_LANGUAGE] ?: return
        val firstReference = javaReferences.firstOrNull() ?: return
        val className = klass.name ?: return
        val elementFactory = JavaPsiFacade.getElementFactory(klass.project)
        val javaSingletonCall = elementFactory.createExpressionFromText("$className.INSTANCE", firstReference)

        javaReferences.filter { it.node.elementType == JavaElementType.NEW_EXPRESSION }
            .forEach {
                when (it.parent.node.elementType) {
                    JavaElementType.EXPRESSION_STATEMENT -> it.delete()
                    else -> it.replace(javaSingletonCall)
                }
            }
    }

    private fun KtSecondaryConstructor.toInitializer(factory: KtPsiFactory): KtClassInitializer? {
        val statements = bodyExpression?.statements.orEmpty()
        if (statements.isEmpty()) return null

        val initializer = factory.createAnonymousInitializer() as? KtClassInitializer ?: return null
        val body = initializer.body as? KtBlockExpression ?: return null
        statements.forEach { statement ->
            body.addBefore(statement.copy(), body.rBrace)
            body.addBefore(factory.createNewLine(), body.rBrace)
        }
        return initializer
    }

    private companion object {
        val JAVA_LANGUAGE: Language? = Language.findLanguageByID("JAVA")
        val KOTLIN_LANGUAGE: Language? = Language.findLanguageByID("kotlin")
    }
}

private class GenerateIdentityEqualsFix : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val klass = descriptor.psiElement.getParentOfType<KtClass>(false) ?: return
        val psiFactory = KtPsiFactory(project)

        val equalsFunction = psiFactory.createFunction(
            KtPsiFactory.CallableBuilder(KtPsiFactory.CallableBuilder.Target.FUNCTION).apply {
                modifier(KtTokens.OVERRIDE_KEYWORD.value)
                typeParams()
                name("equals")
                param("other", "Any?")
                returnType("Boolean")
                blockBody("return this === other")
            }.asString()
        )
        klass.addDeclaration(equalsFunction)

        val hashCodeFunction = psiFactory.createFunction(
            KtPsiFactory.CallableBuilder(KtPsiFactory.CallableBuilder.Target.FUNCTION).apply {
                modifier(KtTokens.OVERRIDE_KEYWORD.value)
                typeParams()
                name("hashCode")
                returnType("Int")
                blockBody("return System.identityHashCode(this)")
            }.asString()
        )
        klass.addDeclaration(hashCodeFunction)
    }

    override fun getFamilyName(): String = KotlinBundle.message("generate.identity.equals.fix.family.name")
}
