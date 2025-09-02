// "Replace usages of 'constructor A<T>(T, T = ...)' in whole project" "true"
// K2_ACTION: "Replace usages of 'A(T, T)' in whole project" "true"
// ERROR: Unresolved reference: T
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Function0<TElement (of class B<TElement>)>', but 'Function0<uninferred T (of class A<T>)>' was expected.
// K2_AFTER_ERROR: Unresolved reference 'T'.

open class A<T> constructor(t: () -> T, f: () -> T = t) {
    @Deprecated("F", ReplaceWith("A<T>({t})"))
    constructor(t: T, f: T = t) : this({ t })
}

class B<TElement>(t: TElement) : A<caret><TElement>(t)

fun b() {
    A<Int>(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageInWholeProjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageInWholeProjectFix