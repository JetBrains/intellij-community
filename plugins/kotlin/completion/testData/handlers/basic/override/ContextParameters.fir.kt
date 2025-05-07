// FIR_COMPARISON

interface Foo {

    context(a: String)
    suspend fun foo(): Unit
}

class Bar : Foo {

    fo<caret>
}

// ELEMENT_TEXT: "override suspend fun foo() {...}"
// TAIL_TEXT: " for String"
// TYPE_TEXT: "Foo"
