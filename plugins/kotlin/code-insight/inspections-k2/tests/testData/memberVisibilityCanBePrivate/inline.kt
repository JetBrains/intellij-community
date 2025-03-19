open class AA {
    protected <warning descr="[NOTHING_TO_INLINE] Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of function types.">inline</warning> fun foo() {
        val result = bar()
    }
    protected fun bar() {
    }
}

fun <T> run(f: () -> T): T = f()

object TT {
    inline fun foo(f: () -> String) {
        run {
            bar(f())
        }
    }

    val x: String
        inline get() = baz

    inline val y: String
        get() = qux

    fun bar(s: String) = s

    val baz = ""

    val qux = ""
}