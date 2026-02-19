interface X

open class Y : X {
    open inner class Inner {
        fun Any.foo(): X {
            if (this@Inner is X) return this@<caret>
        }
    }
}

// IGNORE_K2
// ORDER: this@Y
// ORDER: this@Inner
// ORDER: this@foo
