// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used

fun foo(f: ListWrapper<Int>) {}

class ListWrapper<T>(val x: List<T>)

fun f() {
    foo(ListWrapper<Int>(listOf<caret><Int>()))
}
