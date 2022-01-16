// "Import" "true"
package p

open class Base<T> {
    fun T.genericExt() {}
}

object Obj : Base<String>()

fun usage() {
    "hello".<caret>genericExt()
}
