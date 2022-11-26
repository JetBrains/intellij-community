fun test() {
    if (Foo().check(1, 2)) {
        
    }
}

class Foo {
    fun check(a: Int, b: Int): Boolean {
        return a > b
    }
}