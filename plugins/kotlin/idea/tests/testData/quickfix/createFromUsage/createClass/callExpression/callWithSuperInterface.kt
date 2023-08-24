// "Create class 'Foo'" "true"

interface A

fun test(): A = <caret>Foo(2, "2")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix