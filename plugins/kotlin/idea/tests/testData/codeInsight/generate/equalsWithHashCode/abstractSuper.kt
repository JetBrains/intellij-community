abstract class A {
    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
}

interface I

<caret>class B : A(), I