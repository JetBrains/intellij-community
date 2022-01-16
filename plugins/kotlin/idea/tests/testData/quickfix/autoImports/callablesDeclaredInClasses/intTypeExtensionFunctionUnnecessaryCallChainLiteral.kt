// "Import" "true"
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    14.toInt().<caret>ext()
}
