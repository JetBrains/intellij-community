// IGNORE_K1
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun main() {
    val result = con<caret>
}

// EXIST: {"lookupString":"context","tailText":"(a: A, ..., block: context(A, ...) () -> R) (kotlin)"}
