@DslMarker
annotation class Dsl3

@Dsl3
interface Inter

class ForDsl : Inter

fun foo(f: ForDsl.() -> Unit) {}

fun test() {
    foo {

    }
}