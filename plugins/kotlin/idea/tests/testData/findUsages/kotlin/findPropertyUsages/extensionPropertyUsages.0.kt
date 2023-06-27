// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val String.x: Int"

@file:JvmName("AKt")

val String.<caret>x: Int get() = 1

fun test(s: String) {
    println(s.x)
}