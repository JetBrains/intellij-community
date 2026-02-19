// DISABLE_ERRORS
annotation class Ann(val value: String)

@Ann("value")
expect fun foo()
