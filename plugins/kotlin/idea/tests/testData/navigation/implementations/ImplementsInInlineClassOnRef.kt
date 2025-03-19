fun m(i: I) {
    i.<caret>x()
}
interface I {
    fun x()
}

inline class Foo(val value: Int) : I {
    override fun x() {}
}

// REF: (in Foo).x()