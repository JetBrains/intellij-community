// "Replace with 'new = value'" "true"
@Deprecated("", ReplaceWith("new = value"))
fun Int.old(value: Int) = Unit
var Int.new: Int
    get() = 0
    set(value) = Unit

fun aFunction() {
    1.o<caret>ld(0) // Quick-fix me
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
