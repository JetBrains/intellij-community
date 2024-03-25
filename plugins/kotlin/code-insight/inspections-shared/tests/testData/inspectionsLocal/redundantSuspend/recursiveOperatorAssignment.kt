class A(val x: Int) {
    <caret>suspend operator fun plus(a: A): A {
        var a = a
        a += a
        return a
    }
}