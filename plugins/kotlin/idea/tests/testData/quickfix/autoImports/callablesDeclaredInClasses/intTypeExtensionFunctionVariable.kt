// "Import" "true"
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    val x: Int = 12
    x.<caret>ext()
}
