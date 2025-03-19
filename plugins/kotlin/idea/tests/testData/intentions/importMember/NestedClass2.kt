// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'ppp.Foo.Bar'"
// WITH_STDLIB
// AFTER-WARNING: Check for instance is always 'true'
package ppp

sealed class Foo {
    class Bar(val x: Int) : Foo()
}

fun test() {
    val foo = Foo.Bar(5)

    when (foo) {
        is Foo.<caret>Bar -> println(foo.x)
    }
}
