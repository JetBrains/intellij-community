// "Import extension function 'foobar'" "true"
package p

class T

object TopLevelObject1 {
    fun <A> A.foobar() {}
}

fun usage(t: T) {
    t.<caret>foobar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */