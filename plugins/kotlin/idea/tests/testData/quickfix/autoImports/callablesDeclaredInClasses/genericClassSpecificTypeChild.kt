// "Import extension function 'String.genericExt'" "true"
package p

open class Base<T> {
    fun T.genericExt() {}
}

object Obj : Base<String>()

fun usage() {
    "hello".<caret>genericExt()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix