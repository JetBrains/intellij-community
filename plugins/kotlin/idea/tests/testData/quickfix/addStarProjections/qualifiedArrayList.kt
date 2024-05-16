// "Add '<*>'" "true"
public fun foo(a: Any) {
    a is java.util.Array<caret>List
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddStarProjectionsFixFactory$AddStarProjectionsFix