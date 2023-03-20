fun test() {
    Foo().check(1, 2 + 3).with
}

class Foo {
    fun check(a: Int, b: Int): Boolean {
        return a > b
    }
}