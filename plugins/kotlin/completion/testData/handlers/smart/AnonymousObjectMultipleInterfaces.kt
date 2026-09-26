interface InterfaceA {
    fun fromA()
}

interface InterfaceB {
    fun fromB()
}

interface Child : InterfaceA, InterfaceB {
    fun fromChild()
}

val child : Child = <caret>

// ELEMENT: object