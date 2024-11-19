// "Replace usages of 'Executor.invoke(() -> Unit)' in whole project" "true"

@Deprecated("Use Executor.execute(Runnable) instead.", ReplaceWith("execute(action)"))
public operator fun Executor.invoke(action: () -> Unit) {
}

class Executor {
    fun execute(action: () -> Unit) {}
}

fun usage(executor: Executor) {
    executor<caret> { // `Replace usages in whole project` is not suggested here
        // do something
    }
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageInWholeProjectFix