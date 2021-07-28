interface I1 {
    fun foo()
}

interface I2 {
    fun foo()
}

interface I3: I1, I2

class B : I3 {
    override fun f<caret>oo() {}
}