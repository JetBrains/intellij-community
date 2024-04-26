// "Create secondary constructor" "false"
// ERROR: Too many arguments for public constructor Any() defined in kotlin.Any
// WITH_STDLIB

interface T {

}

class A: T {
    constructor(): super(<caret>1) {

    }
}