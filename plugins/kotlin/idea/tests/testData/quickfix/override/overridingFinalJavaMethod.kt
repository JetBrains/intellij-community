// "class org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParenthesisFix" "false"
// ERROR: 'notify' in 'Object' is final and cannot be overridden
// K2_AFTER_ERROR: 'notify' in 'Object' is final and cannot be overridden.
class A : Object() {
    override<caret> fun notify() {}
}
