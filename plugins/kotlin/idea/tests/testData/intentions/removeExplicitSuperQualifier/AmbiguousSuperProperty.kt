// IS_APPLICABLE: false
// PROBLEM: none

open class B {
    open val v: Int = 0
}

interface I {
    val v: Int
      get() = 0
}

class A : B(), I {
    override val v: Int
        get() = super<B><caret>.v
}