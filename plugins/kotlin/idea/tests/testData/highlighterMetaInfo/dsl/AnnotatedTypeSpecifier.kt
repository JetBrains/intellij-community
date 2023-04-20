@DslMarker
@Target(AnnotationTarget.TYPE)
annotation class Dsl4

class ForDsl

fun foo(f: (@Dsl4 ForDsl).() -> Unit) {}

fun test() {
    foo {

    }
}