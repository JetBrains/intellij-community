// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
const val foo = "prefix" + <caret>"""foo
bar"""
