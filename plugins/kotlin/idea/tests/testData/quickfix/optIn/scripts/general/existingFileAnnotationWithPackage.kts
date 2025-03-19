// "Opt in for 'B' in containing file 'existingFileAnnotationWithPackage.kts'" "true"
// ACTION: Opt in for 'B' in containing file 'existingFileAnnotationWithPackage.kts'
// ACTION: Opt in for 'B' in module 'light_idea_test_case'
// ACTION: Opt in for 'B' on 'h'
// ACTION: Opt in for 'B' on statement
// ACTION: Propagate 'B' opt-in requirement to 'h'
// RUNTIME_WITH_SCRIPT_RUNTIME
@file:OptIn(ExistingFileAnnotationWithPackage.A::class)

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