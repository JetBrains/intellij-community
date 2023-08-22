// PROBLEM: none
class A {
    private open class <caret>B(i: Int)
    private class C(i: Int): B(i)
}