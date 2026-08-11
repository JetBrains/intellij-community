// "Create secondary constructor" "false"
// ERROR: Too many arguments for public constructor Any() defined in kotlin.Any
// WITH_STDLIB
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: TOO_MANY_ARGUMENTS

interface T {

}

class A: T {
    constructor(): super(<caret>1) {

    }
}