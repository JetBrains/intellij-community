// "Import" "true"
package p

open class Base<T> {
    fun T.ext() {}
}

object IntObj : Base<Int>()
object StringObj : Base<String>()

fun usage() {
    "".<caret>ext()
}
