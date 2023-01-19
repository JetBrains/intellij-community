// "Inspection 'Kotlin compiler error' options" "false"
// ACTION: Add use-site target 'receiver'
// ACTION: Convert to block body
// DISABLE-ERRORS
fun <T> elvis(x: T?, y: T): @kotlin.internal.Exact<caret> = TODO()
