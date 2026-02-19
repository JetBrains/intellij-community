// SET_TRUE: USE_TAB_CHARACTER
// SET_INT: TAB_SIZE=4
// SET_INT: INDENT_SIZE=4

fun some() {
	val b = """
		|class Test() {
		|	fun test() {<caret>
		|}
		""".trimMargin()
}

// IGNORE_FORMATTER