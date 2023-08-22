package generic

actual class MyGenericClass<T>

actual fun <T> myFun(): MyGenericClass<T> = MyGenericClass<T>()