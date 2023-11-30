// "Create class 'Foo'" "true"

fun test() {
    val a = <caret>Foo(2, "2") { p: Int -> p + 1 }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix