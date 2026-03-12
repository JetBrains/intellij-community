abstract class AbstractMixed {
    abstract fun required(): String
    fun provided(): Int = 42
}

fun test(): AbstractMixed {
    return <caret>
}

// ELEMENT: object
