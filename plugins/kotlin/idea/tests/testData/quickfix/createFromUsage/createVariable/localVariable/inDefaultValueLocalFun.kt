// "Create local variable 'abc'" "true"
// ACTION: Add '@JvmOverloads' annotation to function 'testMethod'
// ACTION: Create parameter 'abc'
// ACTION: Create property 'abc'
// ACTION: Create property 'abc' as constructor parameter
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Rename reference
// WITH_STDLIB

class Test {
    fun outer() {
        fun testMethod(x:Int = <caret>abc) {

        }
    }
}