// "Create local variable 'abc'" "false"
// ERROR: Unresolved reference: abc
// WITH_STDLIB
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

class Test {
    fun testMethod(x:Int = <caret>abc) {

    }
}