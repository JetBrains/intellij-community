// "Replace with 'shiny() /* Consider better() */'" "true"

@Deprecated("Use shiny() instead", ReplaceWith("shiny() /* Consider better() */"))
fun dep() = Unit

fun shiny() = Unit

fun code() {
    <caret>dep()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
