interface Interface {
    fun <A, B : Runnable, E : Map.Entry<A, B>> foo() where B : Cloneable, B : Comparable<B>
}

class InterfaceImpl : Interface {
    <caret>
}

// MEMBER_K2: "<A, B, E : Map.Entry<A, B>> foo(): Unit B : Runnable, B : Cloneable, B : Comparable<B>"
// MEMBER_K1: "foo(): Unit where B : Cloneable, B : Comparable<B>"