// "Create function 'operate'" "false"
// DISABLE_ERRORS
class OperandA
class OperandB
class OperandC
class Operated {
    operator fun plus(o: OperandA) {}
    operator fun plus(o: OperandB) {}
}

fun operate(p: Any?) {}

fun combine() {
    operate(Operated() + OperandA())
    operate(Operated() <caret>+ OperandC())
}