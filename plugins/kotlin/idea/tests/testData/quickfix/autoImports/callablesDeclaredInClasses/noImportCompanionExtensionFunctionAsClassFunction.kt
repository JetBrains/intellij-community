// "Import" "false"
// ACTION: Create extension function 'A.ext'
// ACTION: Create function 'ext'
// ACTION: Create member function 'A.ext'
// ACTION: Rename reference
// ERROR: Unresolved reference: ext
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

class A {
    companion object
}

open class B {
    fun A.Companion.ext() {}
}

object BObject : B()

fun A.anotherExt() {
    <caret>ext()
}
