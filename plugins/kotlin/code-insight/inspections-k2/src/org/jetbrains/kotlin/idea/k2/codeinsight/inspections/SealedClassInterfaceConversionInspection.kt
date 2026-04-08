// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.fullyExpandedType
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConvertSealedClassToSealedInterfaceInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                if (!isApplicable(klass)) return

                holder.registerProblem(
                    klass,
                    KotlinBundle.message("inspection.convert.sealed.class.to.interface.display.name"),
                    ProblemHighlightType.INFORMATION,
                    ConvertToInterfaceQuickFix(klass.findSealedInheritors())
                )
            }
        }
    }

    private fun isApplicable(klass: KtClass): Boolean {
        if (!klass.isSealed()) return false
        if (klass.isInterface()) return false
        klass.primaryConstructor?.let {
            if (it.valueParameters.isNotEmpty()) return false
            if (it.annotationEntries.isNotEmpty()) return false
        }
        if (klass.secondaryConstructors.isNotEmpty()) return false
        val body = klass.body
        body?.let {
            if (it.properties.any { prop ->
                    prop.hasModifier(KtTokens.FINAL_KEYWORD) || prop.hasModifier(KtTokens.LATEINIT_KEYWORD) ||
                    prop.initializer != null || (!prop.isAbstract() && prop.getter == null) }) return false
            if (it.anonymousInitializers.isNotEmpty()) return false
            if (it.declarations.filterIsInstance<KtNamedFunction>()
                    .any { function -> function.hasModifier(KtTokens.FINAL_KEYWORD) }
            ) return false
        }
        for (entry in klass.superTypeListEntries) {
            if (entry is KtSuperTypeCallEntry) return false
            if (entry is KtDelegatedSuperTypeEntry) return false
        }
        if (klass.superTypeListEntries.any {
                it is KtSuperTypeCallEntry || it is KtDelegatedSuperTypeEntry
            }) return false
        if (klass.findSealedInheritors().any { it.element == null }) return false
        return true
    }

    private class ConvertToInterfaceQuickFix(
        private val inheritors: List<SmartPsiElementPointer<KtClassOrObject>>
    ) : KotlinModCommandQuickFix<KtClass>(), PriorityAction {

        override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.convert.sealed.class.to.interface.fix.text")

        override fun applyFix(project: Project, element: KtClass, updater: ModPsiUpdater) {
            inheritors.mapNotNull { it.element?.let(updater::getWritable) }
                .forEach { it.removeConstructorCallFromSuperType(element) }
            val classKeyword = element.getClassKeyword() as? LeafPsiElement ?: return
            classKeyword.replaceWithText(KtTokens.INTERFACE_KEYWORD.value)
            element.primaryConstructor?.delete()
            element.updateMemberModifiersForInterface()
        }
    }
}

internal class ConvertSealedInterfaceToSealedClassInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                if (!isApplicable(klass)) return

                holder.registerProblem(
                    klass,
                    KotlinBundle.message("inspection.convert.sealed.interface.to.class.display.name"),
                    ProblemHighlightType.INFORMATION,
                    ConvertToClassQuickFix(klass.findSealedInheritors())
                )
            }
        }
    }

    private fun isApplicable(klass: KtClass): Boolean {
        if (!klass.isSealed()) return false
        if (!klass.isInterface()) return false
        // Rejects if the inheritor extends another class, or is interface or enum
        for (pointer in klass.findSealedInheritors()) {
            val inheritor = pointer.element ?: return false
            if (inheritor is KtClass && (inheritor.isInterface() || inheritor.isEnum())) return false
            if (inheritor is KtClass &&
                inheritor.primaryConstructor == null &&
                inheritor.secondaryConstructors.isNotEmpty()
            ) return false
            val hasIssues = analyze(inheritor) {
                inheritor.hasAnotherSuperClass(klass) || inheritor.delegatesSuperType(klass)
            }
            if (hasIssues) return false
        }
        return true
    }

    private class ConvertToClassQuickFix(
        private val inheritors: List<SmartPsiElementPointer<KtClassOrObject>>
    ) : KotlinModCommandQuickFix<KtClass>(), PriorityAction {

        override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.convert.sealed.interface.to.class.fix.text")

        override fun applyFix(project: Project, element: KtClass, updater: ModPsiUpdater) {
            inheritors.mapNotNull { it.element?.let(updater::getWritable) }
                .forEach { it.addConstructorCallToSuperType(element) }
            val interfaceKeyword = element.node.findChildByType(KtTokens.INTERFACE_KEYWORD)?.psi as? LeafPsiElement ?: return
            interfaceKeyword.replaceWithText(KtTokens.CLASS_KEYWORD.value)
            element.updateMemberModifiersForClass()
        }
    }
}

private fun KtClass.findSealedInheritors(): List<SmartPsiElementPointer<KtClassOrObject>> {
    val smartPointerManager = SmartPointerManager.getInstance(project)
    return analyze(this) {
        val symbol = this@findSealedInheritors.symbol as? KaNamedClassSymbol ?: return emptyList()
        symbol.sealedClassInheritors.mapNotNull {
            (it.psi as? KtClassOrObject)?.let (smartPointerManager::createSmartPsiElementPointer)
        }
    }
}

private fun KtClassOrObject.removeConstructorCallFromSuperType(sealedName: KtClass) {
    analyze(this) {
        findSuperTypeEntry<KtSuperTypeCallEntry>(sealedName)?.valueArgumentList?.delete()
    }
    // Remove super() calls from secondary constructors
    if (this is KtClass) {
        secondaryConstructors.forEach { constructor ->
            val delegationCall = constructor.getDelegationCall()
            if (!delegationCall.isCallToThis) {
                constructor.getColon()?.delete()
                delegationCall.delete()
            }
        }
    }
}

private fun KtClassOrObject.addConstructorCallToSuperType(sealedName: KtClass) {
    val entry = analyze(this) {
        findSuperTypeEntry<KtSuperTypeEntry>(sealedName)
    } ?: return
    val typeText = entry.typeReference?.text ?: sealedName.name ?: return
    val callEntry = KtPsiFactory(project).createSuperTypeCallEntry("$typeText()")
    entry.replace(callEntry)
}

context(session: KaSession)
private inline fun <reified T : KtSuperTypeListEntry> KtClassOrObject.findSuperTypeEntry(sealedClass: KtClass): T? {
    val sealedFqName = sealedClass.fqName
    return superTypeListEntries.filterIsInstance<T>().find { entry ->
        entry.resolveToClassSymbol()?.classId?.asSingleFqName() == sealedFqName
    }
}

context(session: KaSession)
private fun KtClassOrObject.hasAnotherSuperClass(sealedClass: KtClass): Boolean {
    return superTypeListEntries.filterIsInstance<KtSuperTypeCallEntry>().any { entry ->
        entry.resolveToClassSymbol()?.psi != sealedClass
    }
}

context(session: KaSession)
private fun KtClassOrObject.delegatesSuperType(sealedClass: KtClass): Boolean {
    return superTypeListEntries.filterIsInstance<KtDelegatedSuperTypeEntry>().any { entry ->
        entry.resolveToClassSymbol()?.psi == sealedClass
    }
}

context(session: KaSession)
private fun KtSuperTypeListEntry.resolveToClassSymbol(): KaNamedClassSymbol? {
    val typeRef = typeReference ?: return null
    val type = typeRef.type.fullyExpandedType
    return (type as? KaClassType)?.symbol as? KaNamedClassSymbol
}

private fun KtClass.updateMemberModifiersForInterface() {
    body?.declarations?.filterIsInstance<KtNamedFunction>()?.forEach { declaration ->
        declaration.removeModifier(KtTokens.OPEN_KEYWORD)
        declaration.removeModifier(KtTokens.ABSTRACT_KEYWORD)
        declaration.removeModifier(KtTokens.PROTECTED_KEYWORD)
    }
    body?.properties?.forEach { property ->
        property.removeModifier(KtTokens.OPEN_KEYWORD)
        property.removeModifier(KtTokens.ABSTRACT_KEYWORD)
        property.removeModifier(KtTokens.PROTECTED_KEYWORD)
    }
}

private fun KtClass.updateMemberModifiersForClass() {
    body?.declarations?.filterIsInstance<KtNamedFunction>()?.forEach { declaration ->
        val isPrivate = declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)
        val isInline = declaration.hasModifier(KtTokens.INLINE_KEYWORD)
        val isOverride = declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)

        if (declaration.hasBody()) {
            if (!isPrivate && !isOverride && !isInline) {
                declaration.addModifier(KtTokens.OPEN_KEYWORD)
            }
        } else {
            declaration.addModifier(KtTokens.ABSTRACT_KEYWORD)
        }
    }
    body?.properties?.forEach { property ->
        val isPrivate = property.hasModifier(KtTokens.PRIVATE_KEYWORD)
        val isOverride = property.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        val hasImplementation = property.initializer != null ||
                property.getter?.hasBody() == true ||
                property.setter?.hasBody() == true

        if (hasImplementation) {
            if (!isPrivate && !isOverride) {
                property.addModifier(KtTokens.OPEN_KEYWORD)
            }
        } else {
            property.addModifier(KtTokens.ABSTRACT_KEYWORD)
        }
    }
}
