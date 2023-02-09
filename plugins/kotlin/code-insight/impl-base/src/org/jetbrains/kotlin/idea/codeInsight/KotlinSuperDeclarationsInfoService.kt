// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.ide.util.EditSourceUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
sealed class SuperDeclaration {
    class Class(override val declaration: SmartPsiElementPointer<KtClassOrObject>): SuperDeclaration()
    class JavaClass(override val declaration: SmartPsiElementPointer<PsiClass>): SuperDeclaration()
    class Function(override val declaration: SmartPsiElementPointer<KtNamedFunction>): SuperDeclaration()
    class JavaMethod(override val declaration: SmartPsiElementPointer<PsiMethod>): SuperDeclaration()
    class Property(override val declaration: SmartPsiElementPointer<KtProperty>): SuperDeclaration()
    class Parameter(override val declaration: SmartPsiElementPointer<KtParameter>): SuperDeclaration()

    abstract val declaration: SmartPsiElementPointer<out PsiElement>

    val descriptor: Navigatable?
        get() {
            val element = declaration.element ?: return null
            return EditSourceUtil.getDescriptor(element)
        }
}

object SuperDeclarationProvider {
    @RequiresReadLock
    @ApiStatus.Internal
    @OptIn(KtAllowAnalysisOnEdt::class)
    fun findSuperDeclarations(declaration: KtDeclaration): List<SuperDeclaration> {
        allowAnalysisOnEdt {
            analyze(declaration) {
                val superSymbols = when (val symbol = declaration.getSymbol()) {
                    is KtValueParameterSymbol -> symbol.generatedPrimaryConstructorProperty?.getDirectlyOverriddenSymbols()?.asSequence()
                        ?: emptySequence()

                    is KtCallableSymbol -> symbol.getDirectlyOverriddenSymbols().asSequence()
                    is KtClassOrObjectSymbol -> symbol.superTypes.asSequence().mapNotNull { (it as? KtNonErrorClassType)?.classSymbol }
                    else -> emptySequence()
                }

                return buildList {
                    for (superSymbol in superSymbols) {
                        if (superSymbol is KtClassOrObjectSymbol && StandardClassIds.Any == superSymbol.classIdIfNonLocal) {
                            continue
                        }
                        when (val psi = superSymbol.psi) {
                            is KtClassOrObject -> add(SuperDeclaration.Class(psi.createSmartPointer()))
                            is KtNamedFunction -> add(SuperDeclaration.Function(psi.createSmartPointer()))
                            is KtProperty -> add(SuperDeclaration.Property(psi.createSmartPointer()))
                            is PsiMethod -> add(SuperDeclaration.JavaMethod(psi.createSmartPointer()))
                            is PsiClass -> add(SuperDeclaration.JavaClass(psi.createSmartPointer()))
                            is KtParameter -> add(SuperDeclaration.Parameter(psi.createSmartPointer()))
                        }
                    }
                }
            }
        }
    }
}