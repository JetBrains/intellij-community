// "Create object 'Foo'" "true"
// DISABLE-ERRORS

open class B

class A {
    var x: B by <caret>Foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix