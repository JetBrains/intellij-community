// "Replace with '`Bar-Baz`::class.java'" "true"
// WITH_STDLIB
class Foo {
    companion object `Bar-Baz`
}

fun test() {
    Foo.javaClass<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithCompanionClassJavaFix
