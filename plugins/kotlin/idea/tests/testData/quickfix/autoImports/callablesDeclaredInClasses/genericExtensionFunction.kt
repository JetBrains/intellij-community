// "Import extension function 'ext'" "true"
package p

open class A {
    fun <T> T.ext() {}
}

object AObject : A()

fun usage() {
    10.<caret>ext()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */