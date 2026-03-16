package testData.libraries

fun <T> T.filter(predicate: (T)-> Boolean) : T? = this

context(_: String)
public fun Int.foo() {}