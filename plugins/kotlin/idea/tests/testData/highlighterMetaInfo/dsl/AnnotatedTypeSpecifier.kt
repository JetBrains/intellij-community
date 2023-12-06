// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
@DslMarker
@Target(AnnotationTarget.TYPE)
annotation class Dsl4

class ForDsl

fun foo(f: (@Dsl4 ForDsl).() -> Unit) {}

fun test() {
    foo {

    }
}