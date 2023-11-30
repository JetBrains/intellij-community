// "Import extension property 'foobar'" "true"
package p

class T

object TopLevelObject1 {
    val <A> A.foobar get() = 10
}

fun usage(t: T) {
    t.<caret>foobar
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */