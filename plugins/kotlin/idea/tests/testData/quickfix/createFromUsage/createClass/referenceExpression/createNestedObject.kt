// "Create object 'Nested'" "true"
class A {
    // TARGET_PARENT:
    class B {
        val a = <caret>Nested
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix