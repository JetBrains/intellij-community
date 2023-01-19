interface Interface {
    fun <A, B : Runnable, E : Map.Entry<A, B>> foo() where B : Cloneable, B : Comparable<B>
}

class InterfaceImpl : Interface {
    <caret>
}