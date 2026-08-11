// "Opt in for 'A' in containing file 'appendFileAnnotationToOtherFileAnnotations.kt'" "true"
// WITH_STDLIB
// K2_ERROR: OPT_IN_USAGE_ERROR

@file:JvmName("Foo")

package p

@RequiresOptIn
annotation class A

@A
fun f() {}

fun g() {
    <caret>f()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix