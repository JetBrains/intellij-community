// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:-IntrinsicConstEvaluation
object Test {
    const val foo = <caret>"""foo
bar
baz"""
}
