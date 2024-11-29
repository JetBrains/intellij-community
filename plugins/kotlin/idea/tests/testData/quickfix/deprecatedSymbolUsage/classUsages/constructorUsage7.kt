// "Replace usages of 'constructor A(Int)' in whole project" "true"
// K2_ACTION: "Replace usages of 'A(Int)' in whole project" "true"

open class A(val b: String, val i: () -> Int) {
    @Deprecated("Replace with primary constructor", ReplaceWith("A(b = \"\") { i }"))
    constructor(i: Int) : this("", { i })
}

class B : A<caret>(i = 33)

fun a() {
    A(42)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageInWholeProjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageInWholeProjectFix