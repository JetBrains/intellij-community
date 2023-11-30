// "Import function 'RegObject.regularFun'" "true"
package p

open class Reg {
    fun regularFun() {}
}

object RegObject : Reg()

fun usage() {
    <caret>regularFun()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */