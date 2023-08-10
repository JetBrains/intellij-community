// PROBLEM: none
package my.simple.name

val foo = 3

class A {
    fun a() = my.simple.name<caret>.foo

    companion object {
        val foo = 7
    }
}
