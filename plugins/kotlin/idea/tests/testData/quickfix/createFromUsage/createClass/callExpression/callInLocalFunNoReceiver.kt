// "Create class 'Foo'" "true"

fun test() {
    fun nestedTest() = <caret>Foo(2, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix