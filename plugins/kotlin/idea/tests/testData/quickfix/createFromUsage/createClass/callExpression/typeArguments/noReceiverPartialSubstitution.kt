// "Create class 'Foo'" "true"

fun test() = <caret>Foo<Int>(2, "2")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix