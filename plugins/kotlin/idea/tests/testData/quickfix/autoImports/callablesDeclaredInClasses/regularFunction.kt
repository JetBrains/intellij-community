// "Import" "true"
package p

open class Reg {
    fun regularFun() {}
}

object RegObject : Reg()

fun usage() {
    <caret>regularFun()
}
