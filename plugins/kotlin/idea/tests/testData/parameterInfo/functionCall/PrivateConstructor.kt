class A private constructor(f: Boolean) {
    constructor(): this(true)
}

fun test() {
    val a = A(<caret>)
}

