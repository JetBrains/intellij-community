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
            val (test3, test4) = Test()
            test3.f(B.getB())
            test.f(B.getB())
            test2.f(B.getB())
            val a = arrayOf(Test())
            val b = mapOf(Pair(Test(), 1))
            for (i in a) {
                for ((j, k) in b) {
                    i.f(B.getB())
                    j.f(B.getB())
                }
            }

            test.let { i ->
                i.f(B.getB())
            }

            test.let {
                it.f(B.getB())
            }

            when (val whenProperty = test2) {
                test2 -> whenProperty.f(B.getB())
                else -> {
                    whenProperty.f(B.getB())
                }
            }

            globalB.f(B.getB())
            outer.f(B.getB())
            inner.f(B.getB())
            A1.f(B.getB())
            A1.f(B.getB())
            A2.f(B.getB())
        }
    }

    fun d() {
        fun d() {
            call { (destructingEntry1, destructingEntry2), c ->
                destructingEntry1.f(B.getB())
            }

        }
        fun call() {

        }
    }
    private operator fun component1(): Test {
        return Test()
    }

    private operator fun component2(): Test {
        return Test()
    }
}
inline fun call(f: (Test, Test) -> Unit) = f()

