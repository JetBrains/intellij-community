// SET_TRUE: USE_TAB_CHARACTER
// SET_INT: TAB_SIZE=4
// SET_INT: INDENT_SIZE=4

val a =
	"""
	  |  blah blah {<caret>}
	""".trimMargin()

// IGNORE_FORMATTER