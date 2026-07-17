// "Import" "false"
// ACTION: Create function 'ext'
// ACTION: Rename reference
// ERROR: Unresolved reference: ext
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

open class Foo {
    fun Int.ext() {}
}

object FooObject : Foo()

fun String.anotherExt() {
    <caret>ext()
}
