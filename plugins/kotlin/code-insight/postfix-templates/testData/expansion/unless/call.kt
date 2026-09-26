fun test() {
    Foo().check(1, 2)<caret>
}

class Foo {
    fun check(a: Int, b: Int): Boolean {
        return a > b
    }
}