// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
@DslMarker
annotation class Dsl1

@DslMarker
annotation class Dsl2

@Dsl1
class ForDsl1

@Dsl2
class ForDsl2

fun foo(f: ForDsl1.() -> Unit) {}

fun bar(f: ForDsl2.() -> Unit) {}

fun test() {
    foo {
        bar {
            bar {
                foo {

                }
            }
        }
    }
}