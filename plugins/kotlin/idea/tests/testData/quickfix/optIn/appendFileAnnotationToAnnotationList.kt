// "Opt in for 'A' in containing file 'appendFileAnnotationToAnnotationList.kt'" "true"
// WITH_STDLIB
// K2_ERROR: OPT_IN_USAGE_ERROR

@file:[
    JvmName("Foo")
    OptIn(B::class)
    Suppress("UNSUPPORTED_FEATURE")
]

package p

@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f() {}

fun g() {
    <caret>f()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix