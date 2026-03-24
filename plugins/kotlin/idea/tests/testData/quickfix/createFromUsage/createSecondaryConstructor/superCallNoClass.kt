// "Create secondary constructor" "false"
// ERROR: Too many arguments for public constructor Any() defined in kotlin.Any
// WITH_STDLIB
// K2_ERROR: Too many arguments for 'constructor(): Any'.
// K2_AFTER_ERROR: Too many arguments for 'constructor(): Any'.

interface T {

}

class A: T {
    constructor(): super(<caret>1) {

    }
}