// "Change function signature to 'suspend fun foo(x: String)'" "true"
interface Foo {
    suspend fun foo(x: String)
}

class Bar : Foo {
    <caret>override fun foo() {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeMemberFunctionSignatureFix