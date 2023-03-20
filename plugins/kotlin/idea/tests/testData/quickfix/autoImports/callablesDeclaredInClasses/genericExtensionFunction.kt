// "Import extension function 'ext'" "true"
package p

open class A {
    fun <T> T.ext() {}
}

object AObject : A()

fun usage() {
    10.<caret>ext()
}
