// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
class MyClass

private val _x = MyClass()
val x: MyClass
    get() = _x<caret>