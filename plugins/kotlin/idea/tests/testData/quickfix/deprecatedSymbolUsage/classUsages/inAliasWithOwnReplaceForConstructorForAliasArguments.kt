// "Replace with 'B'" "true"

class OldClass<T>

@Deprecated("Bad!", ReplaceWith("B"))
class A @Deprecated("Bad!", ReplaceWith("B()")) constructor()

class B

typealias Old = OldClass<<caret>A>

val o: Old = Old()
val a = A() // Usage of A()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix