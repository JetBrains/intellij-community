// "Create abstract function 'foo'" "false"
// K2_AFTER_ERROR: Unresolved reference 'foo'.

open class ParentCtor(p: Int)
abstract class AbstractChildCtor : ParentCtor(f<caret>oo())

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// IGNORE_K1