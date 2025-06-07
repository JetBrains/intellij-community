// "Convert to notNull delegate" "false"
// DISABLE_ERRORS
// ACTION: Make internal
// ACTION: Make private
// ACTION: Remove 'lateinit' modifier
// ACTION: Remove explicit type specification
// ACTION: Remove initializer from property
<caret>lateinit var x: Boolean = true