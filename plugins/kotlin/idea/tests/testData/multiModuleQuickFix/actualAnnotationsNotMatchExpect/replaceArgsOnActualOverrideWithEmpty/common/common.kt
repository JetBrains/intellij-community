// DISABLE_ERRORS
annotation class Ann(val s: String = "default")

@Ann
expect fun foo()
