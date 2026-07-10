// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments
// PROBLEM: none
val contextLambda: context(String) (Int) -> Unit = TODO()

fun usage() {
  context("hello") {
    contextLambda(<caret>10)
  }
}