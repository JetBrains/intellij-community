// "Create function 'operate'" "false"
// DISABLE-ERRORS
class Operated

fun operate(p: Any?) {}

fun combine() {
    operate(<caret>+Operated())
}