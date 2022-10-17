// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.ide.util.EditSourceUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.psi.*

@ApiStatus.Internal
sealed class SuperDeclaration {
    class Class(override val declaration: SmartPsiElementPointer<KtClass>): SuperDeclaration()
    class Function(override val declaration: SmartPsiElementPointer<KtNamedFunction>): SuperDeclaration()
    class Property(override val declaration: SmartPsiElementPointer<KtProperty>): SuperDeclaration()

    abstract val declaration: SmartPsiElementPointer<out KtDeclaration>

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
                    is KtCallableSymbol -> symbol.getDirectlyOverriddenSymbols().asSequence()
                    is KtClassOrObjectSymbol -> symbol.superTypes.asSequence().mapNotNull { (it as? KtNonErrorClassType)?.classSymbol }
                    else -> emptySequence()
                }

                return buildList {
                    for (superSymbol in superSymbols) {
                        when (val psi = superSymbol.psi) {
                            is KtClass -> add(SuperDeclaration.Class(psi.createSmartPointer()))
                            is KtNamedFunction -> add(SuperDeclaration.Function(psi.createSmartPointer()))
                            is KtProperty -> add(SuperDeclaration.Property(psi.createSmartPointer()))
                        }
                    }
                }
            }
        }
    }
}