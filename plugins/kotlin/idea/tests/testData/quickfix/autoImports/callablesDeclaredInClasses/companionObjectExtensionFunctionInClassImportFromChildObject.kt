// "Import extension function 'Companion.foobar'" "true"
package p

open class T {
    companion object
    fun T.Companion.foobar() {}
}

object TObject : T()

fun usage() {
    T.<caret>foobar()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix