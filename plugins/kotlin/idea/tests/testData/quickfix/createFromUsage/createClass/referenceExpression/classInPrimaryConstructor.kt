// "Create class 'X'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
class Foo(x: <caret>X)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction