// FIR_COMPARISON
enum class Foo() {
    FOO {
        override fun foo()<caret>
        {}
    };

    abstract fun foo()
}

// EXIST: override
