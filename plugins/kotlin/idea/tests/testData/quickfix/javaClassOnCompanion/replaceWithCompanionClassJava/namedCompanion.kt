// "Replace with 'Bar::class.java'" "true"
// WITH_STDLIB
class Foo {
    companion object Bar
}

fun test() {
    Foo.javaClass<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithCompanionClassJavaFix