// "Create local variable 'abc'" "false"
// ACTION: Add '@JvmOverloads' annotation to function 'testMethod'
// ACTION: Create parameter 'abc'
// ACTION: Create property 'abc'
// ACTION: Create property 'abc' as constructor parameter
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Rename reference
// ERROR: Unresolved reference: abc
// WITH_STDLIB

class Test {
    fun testMethod(x:Int = <caret>abc) {

    }
}