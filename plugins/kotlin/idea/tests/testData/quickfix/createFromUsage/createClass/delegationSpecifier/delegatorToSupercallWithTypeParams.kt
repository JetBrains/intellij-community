// "Create class 'A'" "true"
// K2_AFTER_ERROR: No type arguments expected for class A : Any.
package p

class Foo: <caret>A<Int, String>(1, "2") {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction