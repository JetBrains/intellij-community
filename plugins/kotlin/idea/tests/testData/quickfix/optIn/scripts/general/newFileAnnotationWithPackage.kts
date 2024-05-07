// "Opt in for 'A' in containing file 'newFileAnnotationWithPackage.kts'" "true"
// ACTION: Opt in for 'A' in containing file 'newFileAnnotationWithPackage.kts'
// ACTION: Opt in for 'A' in module 'light_idea_test_case'
// ACTION: Opt in for 'A' on 'g'
// ACTION: Opt in for 'A' on statement
// ACTION: Propagate 'A' opt-in requirement to 'g'
// RUNTIME_WITH_SCRIPT_RUNTIME
package p

@RequiresOptIn
annotation class A

@A
fun f() {}

fun g() {
    <caret>f()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix