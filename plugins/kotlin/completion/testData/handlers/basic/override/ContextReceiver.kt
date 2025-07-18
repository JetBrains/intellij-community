// FIR_COMPARISON

object Context

interface Foo {

    context(Context)
    suspend fun foo(): Unit
}

class Bar : Foo {

    fo<caret>
}

// ELEMENT_TEXT: "override context(Context) suspend fun foo() {...}"
// TYPE_TEXT: "Foo"
