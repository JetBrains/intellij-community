package testData.libraries

fun <T> T.filter(predicate: (T)-> Boolean) : T? = this

context(String)
public fun Int.foo() {}