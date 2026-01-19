class A {
    inner class XYZ
}

fun A.foo(): A.XYZ {
    return <caret>
}

// ELEMENT: XYZ

// IGNORE_K2
