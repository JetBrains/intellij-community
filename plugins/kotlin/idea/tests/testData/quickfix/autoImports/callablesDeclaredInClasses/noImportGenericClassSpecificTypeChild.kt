// "Import" "false"
// ACTION: Rename reference
// ERROR: Unresolved reference: genericExt
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

open class Base<T> {
    fun T.genericExt() {}
}

object Obj : Base<Int>()

fun usage() {
    "hello".<caret>genericExt()
}
