// "Import extension function 'Int.ext'" "true"
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun f(z: Short): Int = z.toInt()

fun usage() {
    val x = 10
    f(x.toShort()).<caret>ext()
}
