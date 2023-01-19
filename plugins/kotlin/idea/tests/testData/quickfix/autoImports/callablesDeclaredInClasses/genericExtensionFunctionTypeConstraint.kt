// "Import extension function 'ext'" "true"
package p

open class A {
    fun <T : CharSequence> T.ext() {}
}

object AObject : A()

fun usage() {
    "hello".<caret>ext()
}
