// "Opt in for 'B' in containing file 'existingFileAnnotationWithPackage.kt'" "true"
// WITH_STDLIB
@file:OptIn(A::class)

package p

@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f() {}

@B
fun g() {}

fun h() {
    <caret>g()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix