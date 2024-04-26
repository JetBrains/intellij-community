// "Import extension function 'T.foobar'" "true"
package p

class T {
    companion object {
        fun T.foobar() {}
    }
}

fun usage(t: T) {
    t.<caret>foobar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix