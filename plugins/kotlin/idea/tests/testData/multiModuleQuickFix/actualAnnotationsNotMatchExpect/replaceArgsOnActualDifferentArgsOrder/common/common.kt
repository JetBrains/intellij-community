// DISABLE_ERRORS
annotation class Ann(val p1: String, val p2: String)

@Ann(p1 = "1", p2 = "2")
expect fun foo()
