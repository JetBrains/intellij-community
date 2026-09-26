// FIR_IDENTICAL

class Command() {}

fun parse(<warning descr="[UNUSED_PARAMETER]">cmd</warning>: String): Command? { return null  }

fun Any.<warning descr="[EXTENSION_SHADOWED_BY_MEMBER]">equals</warning>(other : Any?) : Boolean = this === other

fun main(<warning descr="[UNUSED_PARAMETER]">args</warning>: Array<String>) {
    val command = parse("")
    if (command == null) <warning descr="[UNUSED_EXPRESSION]">1</warning> // error on this line, but must be OK
}
