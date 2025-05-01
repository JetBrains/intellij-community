// "Create local variable 'abc'" "false"
// ERROR: Unresolved reference: abc
// WITH_STDLIB
// K2_AFTER_ERROR: Unresolved reference 'abc'.

class Test {
    fun testMethod(x:Int = <caret>abc) {

    }
}