// "Import" "false"
// ACTION: Create extension function 'A.ext'
// ACTION: Create function 'ext'
// ACTION: Create member function 'A.ext'
// ACTION: Rename reference
// ERROR: Unresolved reference: ext
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
