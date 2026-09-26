// "class org.jetbrains.kotlin.idea.quickfix.MakeOverriddenMemberOpenFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParenthesisFix" "false"
// ERROR: 'notify' in 'Object' is final and cannot be overridden
// K2_AFTER_ERROR: OVERRIDING_FINAL_MEMBER
// K2_ERROR: OVERRIDING_FINAL_MEMBER
class A : Object() {
    override<caret> fun notify() {}
}
