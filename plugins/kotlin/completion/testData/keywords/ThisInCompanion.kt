// FIR_IDENTICAL
// FIR_COMPARISON
class C {
    companion object {
        fun String.foo() {
            thi<caret> // TODO too many lookup elements for an empty prefix
        }
    }
}

// INVOCATION_COUNT: 1
// EXIST: "this@Companion"
