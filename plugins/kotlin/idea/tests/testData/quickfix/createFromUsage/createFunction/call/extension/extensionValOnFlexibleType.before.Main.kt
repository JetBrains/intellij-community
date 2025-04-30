// "Create extension function 'FooBar.fooBar'" "true"
// ERROR: Type mismatch: inferred type is () -> String but String! was expected
// WITH_STDLIB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

fun m {
    FooBar.Instance.FOO_BAR.fooBar { "<caret>" }
}
// IGNORE_K1