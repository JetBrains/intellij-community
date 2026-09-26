// SHOULD_FAIL_WITH: All inheritors must be nested objects of the class itself and may not inherit from other classes or interfaces. Following problems are found: object A
interface I

sealed class <caret>X {
    object B : X()
}