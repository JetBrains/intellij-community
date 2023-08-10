// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(Int): Int"
enum class E {
    O,
    A {
        init {
            foo(1)
        }

        override fun foo(n: Int): Int = n + 1
    },
    B {
        init {
            foo(1)
        }

        override fun foo(n: Int): Int = n + 2
    };

    init {
        foo(1)
    }

    open fun <caret>foo(n: Int): Int = n
}

fun test(e: E) {
    e.foo(4)
    E.A.foo(4)
    E.O.foo(3)
}
