// SET_TRUE: USE_TAB_CHARACTER
// SET_INT: TAB_SIZE=2
// SET_INT: INDENT_SIZE=2

val a =
	"""
		| blah blah blah
		|  blah blah blah
		|    blah blah blah
		|    <caret>
	"""

// IGNORE_FORMATTER