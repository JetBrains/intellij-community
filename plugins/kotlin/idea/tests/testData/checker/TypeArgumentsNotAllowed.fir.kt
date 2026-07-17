package foo

open class A<T>

fun <T> f(t: <error descr="[TYPE_ARGUMENTS_NOT_ALLOWED]">T<T></error>) {}

fun <T> use(b: <error descr="[WRONG_NUMBER_OF_TYPE_ARGUMENTS]">foo<T>.A<T></error>) {}
