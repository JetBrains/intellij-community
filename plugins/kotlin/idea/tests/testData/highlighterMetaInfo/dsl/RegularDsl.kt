// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
@DslMarker
annotation class Dsl1

@Dsl1
class ForDsl

@Dsl1
fun merge(f: () -> Unit) {
}

fun foo(f: ForDsl.() -> Unit) {}

fun ForDsl.bar(f: () -> Unit) {
}


fun test() {
    foo {

    }
    merge {}
    ForDsl().bar {}
}