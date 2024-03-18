// "Create local variable 'abc'" "false"
// ERROR: Unresolved reference: abc
// WITH_STDLIB

class Test {
    fun testMethod(x:Int = <caret>abc) {

    }
}