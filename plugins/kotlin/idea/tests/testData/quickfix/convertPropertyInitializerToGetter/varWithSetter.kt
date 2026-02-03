// "Convert property initializer to getter" "true"

fun String.foo() = "bar"
fun nop() {

}

interface A {
    var name = <caret>"The quick brown fox jumps over the lazy dog".foo()
        set(value) = nop()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.ConvertPropertyInitializerToGetterIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertPropertyInitializerToGetterFix