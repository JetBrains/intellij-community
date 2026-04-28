// "Import" "false"
// ACTION: Rename reference
// ERROR: Unresolved reference: ext
// K2_ERROR: Unresolved reference 'ext' on receiver of type 'String'.
// K2_AFTER_ERROR: Unresolved reference 'ext' on receiver of type 'String'.
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    // Should not be importable: "hello" is not an Int
    "hello".<caret>ext()
}
