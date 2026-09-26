// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class A<T>

fun test(): A<*> = A<String>()

val x: A<*>
    field<caret> = test()
