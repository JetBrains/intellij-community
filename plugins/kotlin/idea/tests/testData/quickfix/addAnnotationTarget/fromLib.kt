// "Add annotation target" "false"
// WITH_STDLIB
// ACTION: Enable 'Types' inlay hints
// ACTION: Make internal
// ACTION: Specify type explicitly
// ERROR: This annotation is not applicable to target 'top level property without backing field or delegate'

<caret>@JvmField
val x get() = 42
