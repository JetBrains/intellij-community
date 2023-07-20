// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryForDeprecation
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContentAndGetResult
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative
import java.lang.reflect.Modifier

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinInspectionSuppressor
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class KotlinRedundantDiagnosticSuppressInspection : AbstractKotlinInspection() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is KtFile) return null
        val suppressor = LanguageInspectionSuppressors.INSTANCE
            .allForLanguage(file.getLanguage())
            .firstIsInstanceOrNull<KotlinInspectionSuppressor>()
            ?: return null

        val allSuppressedPlaces = file.findAllSuppressedPlaces(suppressor)
        if (allSuppressedPlaces.isEmpty) return null

        val allDiagnostics = file.findAllDiagnostics(allSuppressedPlaces)

        val problems = mutableListOf<ProblemDescriptor>()
        for ((factoryName, suppressedPlaces) in allSuppressedPlaces.entrySet()) {
            val elementsWithDiagnostics = allDiagnostics[factoryName]
            for (suppressedPlace in suppressedPlaces) {
                if (elementsWithDiagnostics.any { suppressor.isSuppressionFor(suppressedPlace, it, factoryName) }) continue
                val highlightingRange = suppressor.getHighlightingRange(suppressedPlace, factoryName) ?: continue
                problems += manager.createProblemDescriptor(
                    suppressedPlace,
                    highlightingRange,
                    InspectionsBundle.message("inspection.redundant.suppression.description"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    suppressor.createRemoveRedundantSuppressionFix(factoryName),
                )
            }
        }

        return problems.toTypedArray()
    }
}

private fun KtFile.findAllDiagnostics(allSuppressedPlaces: MultiMap<String, PsiElement>): MultiMap<String, PsiElement> {
    val places = allSuppressedPlaces.values()
    val firstElement = places.first() as KtAnnotated
    val diagnostics = if (places.all { it === firstElement }) {
        firstElement.analyzeWithContentAndGetResult().bindingContext.diagnostics
    } else {
        analyzeWithAllCompilerChecks().bindingContext.diagnostics
    }

    return MultiMap.create<String, PsiElement>().apply {
        for (diagnostic in diagnostics.noSuppression()) {
            putValue(diagnostic.factoryName.lowercase(), diagnostic.psiElement)
        }
    }
}

private fun KtFile.findAllSuppressedPlaces(suppressor: KotlinInspectionSuppressor): MultiMap<String, PsiElement> {
    val allSuppressedPlaces = MultiMap.createSet<String, PsiElement>()
    this.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            for (id in suppressor.suppressionIds(element)) {
                val idInLowercase = id.lowercase()
                if (idInLowercase !in availableDiagnostics) continue
                allSuppressedPlaces.putValue(idInLowercase, element)
            }

            super.visitElement(element)
        }
    })

    return allSuppressedPlaces
}

private val availableDiagnostics: Set<String> by lazy {
    extractDiagnosticNames(Errors::class.java, ErrorsJvm::class.java, ErrorsJs::class.java, ErrorsNative::class.java)
}

private fun extractDiagnosticNames(vararg classes: Class<*>): Set<String> {
    val set = mutableSetOf<String>()
    for (klass in classes) {
        for (field in klass.declaredFields) {
            if (!Modifier.isStatic(field.modifiers)) continue
            val name = when (val value = field.get(null)) {
                is DiagnosticFactoryForDeprecation<*, *, *> -> value.warningFactory.name
                is DiagnosticFactory<*> -> value.takeUnless { it.severity == Severity.ERROR }?.name
                else -> null
            } ?: continue

            set += name.lowercase()
        }
    }

    return set
}
