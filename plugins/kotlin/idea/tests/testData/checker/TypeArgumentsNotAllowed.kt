package foo

open class A<T>

fun <T> f(<warning descr="[UNUSED_PARAMETER]">t</warning>: T<error descr="[TYPE_ARGUMENTS_NOT_ALLOWED]"><T></error>) {}

fun <T> use(<warning descr="[UNUSED_PARAMETER]">b</warning>: foo<error descr="[TYPE_ARGUMENTS_NOT_ALLOWED]"><T></error>.A<T>) {}
