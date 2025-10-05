// PROBLEM: none
// K2_ERROR: 'infix' modifier is required on 'fun hashCode(): Int'.
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'E (of fun <E> E.foo)'.
// K2_ERROR: Too many arguments for 'fun hashCode(): Int'.
package my.simple.name

fun <T, E, D> foo(a: T, b: E, c: D) = a!!.hashCode() + b!!.hashCode() + c!!.hashCode()

fun <E> E.foo() = this.!!hashCode()

class Outer {
    fun <E> E.foo(x: E, y: E, z: E) = x!!.hashCode() + y!!.hashCode() + z!!.hashCode()

    class Inner {
        fun foo(a: Int, b: Boolean, c: String) = c + a + b

        fun test(): Int {
            fun foo(a: Int, b: Boolean, c: String) = c + a + b
            return my.simple.name<caret>.foo(1, false, "bar")
        }

        companion object {
            fun <T, E, D> foo(a: T, b: E, c: D) = a!!.hashCode() + b!!.hashCode() + c!!.hashCode()
        }
    }
}
