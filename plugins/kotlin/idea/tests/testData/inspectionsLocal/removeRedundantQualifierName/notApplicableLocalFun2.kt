// PROBLEM: none
package my.simple.name

fun say() {}

class Inner {
    fun a() {
        fun say() {}
        fun b() {
            fun say() {}
            my.simple.name<caret>.say()
        }
    }

    companion object {
        fun say() {}
    }
}
