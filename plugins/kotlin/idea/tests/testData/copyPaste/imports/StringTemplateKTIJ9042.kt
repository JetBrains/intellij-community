package from

const val USER = "USER"

annotation class Anno(val s: String)

<selection>@Anno("hasRole('$USER')")
private fun foo() {}
</selection>