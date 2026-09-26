// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class MyClass<in T>

val x: MyClass<Double>
    field<caret> = MyClass<Number>()