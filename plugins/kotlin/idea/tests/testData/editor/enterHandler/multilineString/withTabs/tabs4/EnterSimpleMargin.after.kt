// SET_TRUE: USE_TAB_CHARACTER
// SET_INT: TAB_SIZE=4
// SET_INT: INDENT_SIZE=4

class A {
	val a = """
		<caret>
	""".trimIndent()
}

// IGNORE_FORMATTER