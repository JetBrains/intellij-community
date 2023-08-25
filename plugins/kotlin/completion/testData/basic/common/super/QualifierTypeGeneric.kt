open class A<T>

interface I

class B : A<String>(), I {
    fun foo() {
        super<<caret>
    }
}

// IGNORE_K2
// EXIST: { itemText: "A", tailText: " (<root>)" }
