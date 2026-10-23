// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
const val name = "name"
const val foo = <caret>"""foo
$name"""
