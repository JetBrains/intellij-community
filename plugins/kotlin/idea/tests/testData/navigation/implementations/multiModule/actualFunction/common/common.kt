expect interface ExpIFoo {
    val ibar: String
    fun foo()
}

class Abc : ExpIFoo {
    override fun foo() { }
    override val ibar: String = "bar"
}