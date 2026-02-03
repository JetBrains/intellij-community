// FIR_COMPARISON

object Context

interface Foo {

    context(Context)
    suspend fun foo(): Unit
}

class Bar : Foo {

    fo<caret>
}

// ELEMENT_TEXT: "override suspend fun foo() {...}"
// TAIL_TEXT: " for Context"
// TYPE_TEXT: "Foo"
