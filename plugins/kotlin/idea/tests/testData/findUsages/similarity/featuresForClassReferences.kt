val globalB = Test()
class A1 {
    companion object {

        fun f(b: B) {

        }
    }
}

class A2 {
    companion object {
        fun f(b: B) {

        }
    }
}


class B {
    companion object {
        fun get<caret>B(): B {
            return B()
        }
    }
}

class Test {
    fun f(b: B) {

    }

    fun test(outer: Test) {
        fun(inner: Test) {
            val test = Test()
            val test2 = Test()
            test.f(B.getB())
            test2.f(B.getB())
            globalB.f(B.getB())
            outer.f(B.getB())
            inner.f(B.getB())
            A1.f(B.getB())
            A1.f(B.getB())
            A2.f(B.getB())
        }
    }
}
