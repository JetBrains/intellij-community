// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
@DslMarker
@Target(AnnotationTarget.TYPE)
annotation class Dsl3

open class Super

class ForDsl : Super() {
    fun bar(): Int = 0
}

class ForDsl2 : Super() {
    fun baz(): Int = 1
}

fun <T> foo(f: (@Dsl3 T).() -> Unit) {}

fun test() {
    foo<ForDsl> {
        foo<ForDsl2> {

        }
    }
}