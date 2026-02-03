// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class MyClass<in T>

private val _x = MyClass<Number>()
val x: MyClass<Double>
    get() = _x<caret>