// "Add 'open fun f()' to 'A'" "true"
// K2_ERROR: 'f' overrides nothing.
open class A {
}

fun test() {
    val some = object : A() {
        <caret>override fun f() {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddFunctionToSupertypeFix