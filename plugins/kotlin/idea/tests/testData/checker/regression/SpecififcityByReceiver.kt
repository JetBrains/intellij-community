// FIR_IDENTICAL

fun Any.<warning>equals</warning>(<warning>other</warning> : Any?) : Boolean = true

fun main(<warning>args</warning>: Array<String>) {

    val command : Any = 1

    <warning>command<warning>?.</warning>equals(null)</warning>
    command.equals(null)
}
