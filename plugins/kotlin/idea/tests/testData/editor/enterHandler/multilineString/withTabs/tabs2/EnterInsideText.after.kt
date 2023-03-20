// SET_TRUE: USE_TAB_CHARACTER
// SET_INT: TAB_SIZE=2
// SET_INT: INDENT_SIZE=2

object A {
	val a = """blah
		| <caret>blah""".trimMargin()
}

// IGNORE_FORMATTER