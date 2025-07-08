interface A

fun A.foo(i: Int) = i

class B {
    companion object : A
}

fun test() {
    B.foo(<caret>)
}

