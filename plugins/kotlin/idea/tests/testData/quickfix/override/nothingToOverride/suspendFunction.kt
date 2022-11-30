// "Change function signature to 'suspend fun foo(x: String)'" "true"
interface Foo {
    suspend fun foo(x: String)
}

class Bar : Foo {
    <caret>override fun foo() {
    }
}