package testData.libraries

interface ChildInterface<in K> : AScope, WithFunction<K> {}
fun <T> AScope.withLambda(block: ChildInterface<T>.() -> Unit) {}