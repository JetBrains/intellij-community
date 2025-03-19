// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

fun KotlinMoveSource(declaration: KtNamedDeclaration) = KotlinMoveSource.Elements(listOf(declaration))

fun KotlinMoveSource(declarations: Collection<KtNamedDeclaration>) = KotlinMoveSource.Elements(declarations)

fun KotlinMoveSource(file: KtFile) = KotlinMoveSource.File(file)

sealed interface KotlinMoveSource {
    val elementsToMove: Collection<KtNamedDeclaration>

    class Elements(override val elementsToMove: Collection<KtNamedDeclaration>) : KotlinMoveSource

    class File(val file: KtFile) : KotlinMoveSource {
        override val elementsToMove: Collection<KtNamedDeclaration> get() = file.declarations.filterIsInstance<KtNamedDeclaration>()
    }
}