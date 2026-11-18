// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
// OPTIONS: usages
// CRI_IGNORE
package server

data class Point(val x: Int, val y: Int)

val (<caret>first, second) = Point(1, 2)

fun sameFile(): Int = first + second
