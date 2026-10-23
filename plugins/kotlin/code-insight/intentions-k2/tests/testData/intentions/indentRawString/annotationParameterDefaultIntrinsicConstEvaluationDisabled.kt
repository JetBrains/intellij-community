// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:-IntrinsicConstEvaluation
annotation class Ann(val s: String = <caret>"""foo
bar
baz""")
