// COMPILER_ARGUMENTS: -Xcontext-parameters

annotation class Annotation

@Annotation
fun f(<caret>p: Int) {}
