fun foo(a: Int = 1, b: Int = 2, c: Int) {}

fun test() {
    foo(b = 2<caret>, c = 3, a = 1)
}