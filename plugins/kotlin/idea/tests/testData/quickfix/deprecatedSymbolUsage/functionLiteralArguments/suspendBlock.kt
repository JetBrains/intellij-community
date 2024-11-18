// "Replace with 'withContext(this, block)'" "true"

interface Context
class MyClass: Context
interface Scope

@Deprecated(
    message = "",
    replaceWith = ReplaceWith("withContext(this, block)"),
)
public suspend inline operator fun <T> MyClass.invoke(
    noinline block: suspend Scope.() -> T
): T = withContext(this, block)

public suspend fun <T> withContext(
    context: Context,
    block: suspend Scope.() -> T
): T {}

suspend fun hhh(d: MyClass) {
    d.inv<caret>oke {  }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K1