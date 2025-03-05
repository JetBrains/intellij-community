// DISABLE_ERRORS
annotation class Ann(val value: String)

const val CONSTVAL = "hello"
@Ann(CONSTVAL + " world")
expect fun foo()
