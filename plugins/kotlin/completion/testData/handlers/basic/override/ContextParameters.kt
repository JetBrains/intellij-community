// IGNORE_K1
// FIR_COMPARISON

interface Foo {

    context(a: String)
    suspend fun foo(): Unit
}

class Bar : Foo {

    fo<caret>
}