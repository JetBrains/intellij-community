// "Make '<init>' internal" "false"
// DISABLE-ERRORS
// ACTION: Do not show return expression hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Make 'My' public

internal class My

annotation class Your(val x: <caret>My)