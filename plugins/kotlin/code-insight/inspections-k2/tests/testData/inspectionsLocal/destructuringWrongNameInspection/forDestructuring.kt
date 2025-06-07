// WITH_STDLIB
// K2_ERROR: Passing value as a vararg is allowed only inside a parenthesized argument list.
// K2_AFTER_ERROR: Passing value as a vararg is allowed only inside a parenthesized argument list.
data class Foo(val a: String, val b: Int)

fun bar(f: Foo) {
    for ((r, a<caret>) in listOf(Foo("1", 2)) {

    }
}