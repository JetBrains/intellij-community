// IGNORE_FIR

interface Zoo<T> {
    inner enum class Var : Zoo<T>
}

object Outer {
    fun bar() = Unit
    class Inner  {
        fun foo() = this@Outer.bar()
    }
}