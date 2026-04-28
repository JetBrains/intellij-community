// "Import" "false"
// ACTION: Convert to also
// ACTION: Convert to apply
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Rename reference
// ERROR: Unresolved reference: ext
// K2_ERROR: Unresolved reference 'ext' on receiver of type 'Boolean'.
// K2_AFTER_ERROR: Unresolved reference 'ext' on receiver of type 'Boolean'.

package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    val foo = true
    // should not be importable, foo type is not Int
    foo.<caret>ext()
}
