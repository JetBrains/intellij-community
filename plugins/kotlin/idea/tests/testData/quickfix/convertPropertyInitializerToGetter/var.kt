// "Convert property initializer to getter" "true"
// WITH_STDLIB

fun String.foo() = "bar"

interface A {
    var name = <caret>"The quick brown fox jumps over the lazy dog".foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.ConvertPropertyInitializerToGetterIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertPropertyInitializerToGetterFix