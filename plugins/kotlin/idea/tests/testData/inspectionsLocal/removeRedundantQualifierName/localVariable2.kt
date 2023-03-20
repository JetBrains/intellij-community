package my.simple.name

val foo = 3

class A {
    fun a() = A<caret>.foo

    fun foo() {}

    companion object {
        val foo = 7
    }
}
