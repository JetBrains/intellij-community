// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
@DslMarker
annotation class Dsl1

@Dsl1
class ForDsl

fun foo(f: ForDsl.() -> Unit) {}

fun test() {
    foo {

    }
}