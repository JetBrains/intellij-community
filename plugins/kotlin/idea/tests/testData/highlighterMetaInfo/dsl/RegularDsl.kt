@DslMarker
annotation class Dsl1

@Dsl1
class ForDsl

fun foo(f: ForDsl.() -> Unit) {}

fun test() {
    foo {

    }
}