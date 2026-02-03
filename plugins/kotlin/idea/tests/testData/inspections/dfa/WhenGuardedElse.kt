// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xwhen-guards
private fun checkPass(pass: String?) {
    println(pass)
    val secret =
        when (pass) {
            null -> null
            else if pass.hashCode() == 0x56760663 -> "TOP SECRET DATA"
            else -> null
        }
    if (secret != null) {
    }
}
