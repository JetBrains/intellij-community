// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
@DslMarker
annotation class Dsl2

@Dsl2
open class Super

class ForDsl : Super()

fun foo(f: ForDsl.() -> Unit) {}

fun test() {
    foo {

    }
}