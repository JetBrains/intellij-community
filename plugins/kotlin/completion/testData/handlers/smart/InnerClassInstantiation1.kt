class A {
    inner class XYZ

    fun foo() {
        val v: XYZ = <caret>
    }
}

// ELEMENT: XYZ

// IGNORE_K2
