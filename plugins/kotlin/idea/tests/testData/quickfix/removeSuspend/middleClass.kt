// "Remove 'suspend' modifier from all functions in hierarchy" "true"
// K2_ERROR: Non-suspend function 'foo' cannot override suspend function 'suspend fun foo(): Unit' defined in 'B'.
// K2_ERROR: Non-suspend function 'foo' cannot override suspend function 'suspend fun foo(): Unit' defined in 'B'.
// K2_ERROR: Suspend function 'foo' cannot override non-suspend function 'fun foo(): Unit' defined in 'A'.
open class A {
    open fun foo() {

    }

    open fun foo(n: Int) {

    }
}

open class B : A() {
    override suspend fun <caret>foo() {

    }

    override fun foo(n: Int) {

    }
}

open class B1 : A() {
    override fun foo() {

    }

    override fun foo(n: Int) {

    }
}

open class C : B() {
    override fun foo() {

    }

    override fun foo(n: Int) {

    }
}

open class C1 : B() {
    override fun foo() {

    }

    override fun foo(n: Int) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeSuspendInHierarchyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuspendInHierarchyFix