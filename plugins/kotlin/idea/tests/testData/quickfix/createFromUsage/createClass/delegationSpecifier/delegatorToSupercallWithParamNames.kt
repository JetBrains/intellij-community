// "Create class 'A'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
package p

class B {

}

class Foo: <caret>A(abc = 1, ghi = "2", def = B()) {

}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction