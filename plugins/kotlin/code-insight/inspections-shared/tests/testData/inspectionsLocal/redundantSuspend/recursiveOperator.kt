class A(val x: Int) {
    <caret>suspend operator fun plus(a: A): A {
        return a + a
    }
}