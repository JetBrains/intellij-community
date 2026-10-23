// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
@Ann("""foo
bar
baz"""<caret>)
class Test

annotation class Ann(val s: String)
