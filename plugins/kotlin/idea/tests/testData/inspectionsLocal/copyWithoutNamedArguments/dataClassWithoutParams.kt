// PROBLEM: none
// ERROR: Data class must have at least one primary constructor parameter
// ERROR: Too many arguments for public final fun copy(): Foo defined in Foo
// K2_ERROR: DATA_CLASS_WITHOUT_PARAMETERS
// K2_ERROR: TOO_MANY_ARGUMENTS

data class Foo()

fun bar(f: Foo) {
    f.co<caret>py("")
}

