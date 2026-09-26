// FIR_IDENTICAL

fun Any.<warning descr="[EXTENSION_SHADOWED_BY_MEMBER]">equals</warning>(<warning descr="[UNUSED_PARAMETER]">other</warning> : Any?) : Boolean = true

fun main(<warning descr="[UNUSED_PARAMETER]">args</warning>: Array<String>) {

    val command : Any = 1

    command<warning descr="[UNNECESSARY_SAFE_CALL]">?.</warning>equals(null)
    command.equals(null)
}
