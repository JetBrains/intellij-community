// FIR_IDENTICAL
// FIR_COMPARISON
interface I {
    suspend fun foo()
}

class A : I {
    o<caret>
}

// ELEMENT_TEXT: "override suspend fun foo() {...}"
