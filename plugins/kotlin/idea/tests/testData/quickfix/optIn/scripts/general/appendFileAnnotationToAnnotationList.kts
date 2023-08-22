// "Opt in for 'A' in containing file 'appendFileAnnotationToAnnotationList.kts'" "true"
// ACTION: Add '-opt-in=p.AppendFileAnnotationToAnnotationList.A' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'A' in containing file 'appendFileAnnotationToAnnotationList.kts'
// ACTION: Opt in for 'A' on 'g'
// ACTION: Opt in for 'A' on statement
// ACTION: Propagate 'A' opt-in requirement to 'g'
// RUNTIME_WITH_SCRIPT_RUNTIME
@file:[
    JvmName("Foo")
    OptIn(AppendFileAnnotationToAnnotationList.B::class)
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