// "Replace with 'C'" "true"
@Deprecated("", replaceWith = ReplaceWith("C"))
private typealias A = C

private class C {
    companion object {
        val x = 1
    }
}

fun f() {
    val x = <caret>A.x
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix