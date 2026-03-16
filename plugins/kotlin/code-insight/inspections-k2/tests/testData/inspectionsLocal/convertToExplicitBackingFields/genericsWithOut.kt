// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class MyClass<out T>

private val _y = MyClass<String>()
val y: MyClass<Any>
    get() = _y<caret>