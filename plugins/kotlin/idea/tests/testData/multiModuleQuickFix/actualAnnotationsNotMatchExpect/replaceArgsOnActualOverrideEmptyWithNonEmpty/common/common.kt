// DISABLE_ERRORS
annotation class Ann(val s: String = "default")

@Ann("different")
expect fun foo()
