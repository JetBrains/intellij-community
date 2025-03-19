// "Replace usages of 'myJavaClass(): Class<T>' in whole project" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("T::class.java"))
inline fun <reified T: Any> myJavaClass(): Class<T> = T::class.java

fun foo() {
    val v1 = <caret>myJavaClass<List<*>>()
    val v2 = myJavaClass<List<String>>()
    val v3 = myJavaClass<Array<String>>()
    val v4 = myJavaClass<java.util.Random>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageInWholeProjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageInWholeProjectFix