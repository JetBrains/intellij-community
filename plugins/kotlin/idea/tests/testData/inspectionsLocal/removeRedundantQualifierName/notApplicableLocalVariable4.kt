// PROBLEM: none
package my.simple.name

val foo = 3

class A {
    fun a() = A<caret>.foo

    val foo = 10

    companion object {
        val foo = 7
    }
}
