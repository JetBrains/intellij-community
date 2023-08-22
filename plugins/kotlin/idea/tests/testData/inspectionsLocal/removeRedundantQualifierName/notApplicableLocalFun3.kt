// PROBLEM: none
package my.simple.name

fun say() {}

class Inner {
    fun a() {
        fun Int.say() {}
        fun b() {
            fun say() {}
            3<caret>.say()
        }
    }

    companion object {
        fun say() {}
    }
}
