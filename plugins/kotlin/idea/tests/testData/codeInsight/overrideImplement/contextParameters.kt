// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

interface Foo {
    context(a: String)
    fun foo()
}

class Bar: Foo {
    <caret>
}

// MEMBER: "context(a: String)\n foo(): Unit"