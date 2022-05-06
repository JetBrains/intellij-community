// "Convert to notNull delegate" "false"
// DISABLE-ERRORS
// ACTION: Do not show return expression hints
// ACTION: Make internal
// ACTION: Make not-nullable
// ACTION: Make private
// ACTION: Remove 'lateinit' modifier
<caret>lateinit var x: Boolean?