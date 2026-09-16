// "Import extension function 'foobar'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
package p

class T

object TopLevelObject1 {
    fun <A> A.foobar() {}
}

fun usage(t: T) {
    t.<caret>foobar()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix