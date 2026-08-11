package test

class A {
    constructor(x: Int) : <warning descr="[DEPRECATION]">this</warning>(x, 0) {
    }

    @Deprecated("use one-arg overload")
    constructor(x: Int, y: Int) {
        x + y
    }
}

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS
