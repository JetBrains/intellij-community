// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.js

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class NonExternalClassifierExtendingStateOrPropsInspection :
    KotlinApplicableInspectionBase<KtClassOrObject, NonExternalClassifierExtendingStateOrPropsInspection.Context>() {

    data class Context(val isInterface: Boolean, val hasQuickFix: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        classOrObjectVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtClassOrObject): Boolean =
        element.platform.isJs() && (element is KtClass || element is KtObjectDeclaration)

    override fun KaSession.prepareContext(element: KtClassOrObject): Context? {
        val symbol = element.symbol as? KaClassSymbol ?: return null
        if (!implementsReactPropsOrState(symbol)) return null

        val isExternalInterface = element is KtClass && element.isInterface() && element.hasModifier(KtTokens.EXTERNAL_KEYWORD)
        if (isExternalInterface) return null

        val isInterface = element is KtClass && element.isInterface()
        val canAddExternal = isInterface && !element.hasModifier(KtTokens.EXTERNAL_KEYWORD)

        return Context(isInterface = isInterface, hasQuickFix = canAddExternal)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtClassOrObject,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val description = when {
            context.isInterface -> KotlinBundle.message("interface.should.be.external")
            element is KtClass -> KotlinBundle.message("class.should.be.external.interface")
            else -> KotlinBundle.message("object.should.be.external.interface")
        }

        val fixes = if (context.hasQuickFix) arrayOf(AddExternalKeywordFix()) else emptyArray()

        return createProblemDescriptor(
            element, rangeInElement, description,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly, *fixes
        )
    }
}

private class AddExternalKeywordFix : KotlinModCommandQuickFix<KtClassOrObject>() {
    override fun getFamilyName(): String = KotlinBundle.message("add.external.keyword")

    override fun applyFix(project: Project, element: KtClassOrObject, updater: ModPsiUpdater) {
        element.addModifier(KtTokens.EXTERNAL_KEYWORD)
    }
}

private class ChangeToVarFix : KotlinModCommandQuickFix<KtProperty>() {
    override fun getFamilyName(): String = KotlinBundle.message("change.to.var")

    override fun applyFix(project: Project, element: KtProperty, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(project)
        val newKeyword = factory.createVarKeyword()
        element.valOrVarKeyword.replace(newKeyword)
        if (element.hasModifier(KtTokens.CONST_KEYWORD)) {
            element.removeModifier(KtTokens.CONST_KEYWORD)
        }
    }
}


internal class NonVarPropertyInExternalInterfaceInspection :
    KotlinApplicableInspectionBase<KtProperty, Unit>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) = visitTargetElement(property, holder, isOnTheFly)
        }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (!element.platform.isJs() || element.isVar) return false
        val parent = element.containingClassOrObject as? KtClass ?: return false
        return parent.isInterface() && parent.hasModifier(KtTokens.EXTERNAL_KEYWORD)
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        val parent = element.containingClassOrObject as? KtClass ?: return null
        val symbol = parent.symbol as? KaClassSymbol ?: return null
        return if (implementsReactPropsOrState(symbol)) Unit else null
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtProperty,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            element, rangeInElement,
            KotlinBundle.message("property.in.external.interface.should.be.var"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly,
            ChangeToVarFix()
        )
    }
}


internal class NonNullableBooleanPropertyInExternalInterfaceInspection :
    KotlinApplicableInspectionBase<KtProperty, Unit>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) = visitTargetElement(property, holder, isOnTheFly)
        }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (!element.platform.isJs() || element.typeReference == null) return false
        val parent = element.containingClassOrObject as? KtClass ?: return false
        return parent.isInterface() && parent.hasModifier(KtTokens.EXTERNAL_KEYWORD)
    }

    override fun KaSession.prepareContext(element: KtProperty): Unit? {
        val type = element.returnType
        val isBoolean = (type as? KaClassType)?.classId == StandardClassIds.Boolean
        val isNullable = type.isMarkedNullable
        return if (isBoolean && !isNullable) Unit else null
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtProperty,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            element, rangeInElement,
            KotlinBundle.message("boolean.property.in.external.interface.should.be.nullable"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly,
            ConvertToNullableTypeFix()
        )
    }
}

private class ConvertToNullableTypeFix : KotlinModCommandQuickFix<KtProperty>() {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.nullable.type")

    override fun applyFix(project: Project, element: KtProperty, updater: ModPsiUpdater) {
        val typeRef = element.typeReference ?: return
        val factory = KtPsiFactory(project)
        val newType = factory.createType(typeRef.text + "?")
        typeRef.replace(newType)
    }
}

private val REACT_PACKAGE_ID = Name.identifier("react")
private val REACT_PACKAGE = FqName(REACT_PACKAGE_ID.identifier)
private val R_PROPS = REACT_PACKAGE.child(Name.identifier("RProps")).asClassId()
private val R_STATE = REACT_PACKAGE.child(Name.identifier("RState")).asClassId()
private val STATE = REACT_PACKAGE.child(Name.identifier("State")).asClassId()
private val PROPS = REACT_PACKAGE.child(Name.identifier("Props")).asClassId()

private fun FqName.asClassId(): ClassId = ClassId.topLevel(this)

private fun isReactPropsOrState(classId: ClassId?): Boolean {
    return classId == PROPS || classId == R_PROPS || classId == STATE || classId == R_STATE
}

private fun implementsReactPropsOrState(symbol: KaClassSymbol): Boolean {
    val visited = mutableSetOf<KaClassSymbol>()
    val toCheck = mutableListOf(symbol)

    while (toCheck.isNotEmpty()) {
        val current = toCheck.removeAt(0)
        if (!visited.add(current)) continue

        for (type in current.superTypes) {
            val classType = type as? KaClassType ?: continue
            val superSymbol = classType.symbol as? KaClassSymbol ?: continue
            val classId = superSymbol.classId

            if (isReactPropsOrState(classId)) return true
            toCheck.add(superSymbol)
        }
    }
    return false
}
