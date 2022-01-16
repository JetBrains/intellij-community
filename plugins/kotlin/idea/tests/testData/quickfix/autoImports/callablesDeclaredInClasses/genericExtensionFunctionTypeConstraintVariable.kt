// "Import" "true"
package p

open class A {
    fun <T : CharSequence> T.ext() {}
}

object AObject : A()

fun usage() {
    val hello = "hi"
    hello.<caret>ext()
}
