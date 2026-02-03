// FIR_COMPARISON
// FIR_IDENTICAL
package p

class B

class R {
    companion object {
        fun B.f() {
            this.<caret>
        }
    }
}

// EXIST: { itemText: "f", tailText: "() for B in R.Companion" }
