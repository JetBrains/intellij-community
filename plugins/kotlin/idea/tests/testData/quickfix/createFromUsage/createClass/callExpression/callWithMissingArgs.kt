// "Create class 'Foo'" "false"
// ERROR: No value passed for parameter 's'

class Foo(i: Int, s: String)

fun test() {
    val a = Foo(2<caret>)
}