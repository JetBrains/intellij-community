// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

fun m(p: <caret>(String) -> Unit) {

}

// INVOCATION_COUNT: 1
// EXIST: context
// ABSENT: const