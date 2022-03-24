// "Import" "true"
package p

open class Reg {
    val regularProperty = 1
}

object RegObject : Reg()

fun usage() {
    val x = <caret>regularProperty
}
