// "Opt in for 'A' in containing file 'newFileAnnotationWithPackage.kt'" "true"
// WITH_STDLIB
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@p.A' or '@OptIn(p.A::class)'
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