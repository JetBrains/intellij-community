// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// FIR_COMPARISON
@file:JvmName("AKt")

val String.<caret>x: Int get() = 1
