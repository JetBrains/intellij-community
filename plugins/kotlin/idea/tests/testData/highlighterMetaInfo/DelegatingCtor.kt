// IGNORE_FIR

open class Foo {
    constructor(i: Int)
}

class Bar : Foo {
    constructor(s: String)
}

class F(foo: String) {
    constructor() {}
}

enum class E(val a: String) {
    A;
    constructor()
}
