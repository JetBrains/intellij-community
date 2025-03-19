// "Replace with 'A<TElement>({t})'" "true"
// K2_ACTION: "Replace with 'A<T>({t})'" "true"

open class A<T> constructor(t: () -> T) {
    @Deprecated("F", ReplaceWith("A<T>({t})"))
    constructor(t: T) : this({ t })
}

class B<TElement>(t: TElement) : A<caret><TElement>(t)

fun b() {
    A<Int>(42)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2