// PROBLEM: none
// ERROR: Data class must have at least one primary constructor parameter
// ERROR: Too many arguments for public final fun copy(): Foo defined in Foo
// K2_ERROR: Data class must have at least one primary constructor parameter.
// K2_ERROR: Too many arguments for 'fun copy(): Foo'.

data class Foo()

fun bar(f: Foo) {
    f.co<caret>py("")
}

// IGNORE_K1