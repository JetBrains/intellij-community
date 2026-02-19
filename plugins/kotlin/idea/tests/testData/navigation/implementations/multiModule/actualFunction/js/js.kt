actual interface ExpIFoo {
    actual val ib<caret>ar: String
    actual fun foo()
}

class Abc : ExpIFoo {
    override fun foo() { }
    override val ibar: String = "bar"
}

// REF: [testModule_JS] (in Abc).ibar