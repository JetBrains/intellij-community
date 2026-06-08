// "Replace with 'isVisible = visible'" "true"
@Deprecated("Nice description here", replaceWith = ReplaceWith("isVisible = visible"))
fun C.something(visible: Boolean)  {
    // Something useful here
}

class C(){
    var isVisible: Boolean = false
}

fun use(){
    C().somet<caret>hing(false)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
