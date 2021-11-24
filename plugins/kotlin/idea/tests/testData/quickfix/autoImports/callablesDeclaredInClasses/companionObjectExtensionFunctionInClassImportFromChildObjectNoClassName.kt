// "Import" "true"
package p

open class T {
    companion object
    fun Companion.foobar() {}
}

object TObject : T()

fun usage() {
    T.<caret>foobar()
}
