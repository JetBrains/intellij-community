// "Opt in for 'A' in containing file 'appendFileAnnotationWithAnnotationList.kts'" "true"
// ACTION: Add full qualifier
// ACTION: Introduce import alias
// ACTION: Opt in for 'A' in containing file 'appendFileAnnotationWithAnnotationList.kts'
// ACTION: Opt in for 'A' in module 'light_idea_test_case'
// ACTION: Opt in for 'A' on 'g'
// ACTION: Opt in for 'A' on statement
// ACTION: Propagate 'A' opt-in requirement to 'g'
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: OPT_IN_USAGE_ERROR
@file:[
    JvmName("Foo")
]

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