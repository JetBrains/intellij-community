actual interface ExpIFoo {
    actual val ibar: String
    actual fun fo<caret>o()
}

class Abc : ExpIFoo {
    override fun foo() { }
    override val ibar: String = "bar"
}

// REF: [testModule_JS] (in Abc).foo()

// K2_REF: [testModule_JS] (in Abc).foo()
// K2_REF: [testModule_Common] (in Abc).foo()