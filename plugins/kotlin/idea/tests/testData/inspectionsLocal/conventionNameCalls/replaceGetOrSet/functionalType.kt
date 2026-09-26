// PROBLEM: none

private data class Test(
    val get: (String?, String?) -> String?,
)
private val foo = Test { _, _ -> null }.g<caret>et(null, null)