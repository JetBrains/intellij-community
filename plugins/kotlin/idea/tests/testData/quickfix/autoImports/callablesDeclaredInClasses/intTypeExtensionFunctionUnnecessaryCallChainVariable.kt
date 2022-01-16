// "Import" "true"
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    val x = 10
    x.toInt().<caret>ext()
}
