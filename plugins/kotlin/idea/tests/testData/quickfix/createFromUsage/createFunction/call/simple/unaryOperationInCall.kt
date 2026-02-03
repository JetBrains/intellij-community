// "Create function 'operate'" "false"
// DISABLE_ERRORS
class Operated

fun operate(p: Any?) {}

fun combine() {
    operate(<caret>+Operated())
}