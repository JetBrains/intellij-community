// "Import" "false"
// ACTION: Rename reference
// ERROR: Unresolved reference: ext
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    // Should not be importable: "hello" is not an Int
    "hello".<caret>ext()
}
