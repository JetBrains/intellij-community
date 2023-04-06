package testData.libraries

public inline fun <T> T.filter(predicate: (T)-> Boolean) : T? = this