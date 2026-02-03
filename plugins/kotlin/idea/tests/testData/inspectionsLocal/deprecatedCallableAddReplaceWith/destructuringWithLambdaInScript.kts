// PROBLEM: none
// WITH_STDLIB
// SKIP_ERRORS_BEFORE
// SKIP_ERRORS_AFTER

val (x, y) = run <caret>{

}

fun run(f: () -> Unit) = f()
