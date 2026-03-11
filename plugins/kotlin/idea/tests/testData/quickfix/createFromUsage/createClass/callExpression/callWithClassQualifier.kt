// "Create class 'Foo'" "true"
// KEEP_ACTIONS_LIST_ORDER
// K2_ACTIONS_LIST: Create annotation 'Foo'
// K2_ACTIONS_LIST: Create class 'Foo'
// K2_ACTIONS_LIST: Create enum 'Foo'
// K2_ACTIONS_LIST: Create interface 'Foo'
// K2_ACTIONS_LIST: Create parameter 'Foo'
// K2_ACTIONS_LIST: Rename reference
// K2_ACTIONS_LIST: Create extension function 'A.Companion.Foo'
// K2_ACTIONS_LIST: Create member function 'A.Companion.Foo'
// K2_ERROR: Unresolved reference 'Foo'.
class A<T>(val n: T) {

}

fun test() {
    val a = A.<caret>Foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction