// "Create class 'Bar'" "true"
// DISABLE-ERRORS
private class Foo

val bar = <caret>Bar(Foo())

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix