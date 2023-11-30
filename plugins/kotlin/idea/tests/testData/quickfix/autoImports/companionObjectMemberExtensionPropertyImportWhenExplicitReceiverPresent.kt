// "Import extension property 'T.foobar'" "true"
package p

class T {
    companion object {
        val T.foobar get = 10
    }
}

fun usage(t: T) {
    t.<caret>foobar
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */