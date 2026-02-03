// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package a.b.c

interface I1 {
    fun foo()
}

interface I2

fun Any.test() {
    if (this is I1 && this is I2) {
        this.foo()
        foo()
    }
}

