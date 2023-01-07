// "Replace with 'arrayOf'" "false"
// ACTION: Convert to vararg parameter
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Flip ',' (may change semantics)
annotation class Ann(val x: IntArray = [1,<caret> 2, 3])
