package test

interface NoImplementationInterface {
    fun foo(): Int
    fun some(): String
}

// SEARCH_TEXT: NoImplemen
// REF: (test).NoImplementationInterface