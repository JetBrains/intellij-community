actual interface ExpIFoo {
    actual val ibar: String
    actual fun foo()
}

class Abc : ExpIFoo {
    override fun foo() { }
    override val ibar: String = "bar"
}