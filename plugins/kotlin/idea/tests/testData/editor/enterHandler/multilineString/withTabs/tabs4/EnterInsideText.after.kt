// SET_TRUE: USE_TAB_CHARACTER
// SET_INT: TAB_SIZE=4
// SET_INT: INDENT_SIZE=4

object A {
	val a = """blah
		| <caret>blah""".trimMargin()
}

// IGNORE_FORMATTER