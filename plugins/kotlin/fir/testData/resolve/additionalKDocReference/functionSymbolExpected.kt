package test

fun foo() = 7

/**
 * [not.exist.fo<caret>o] actually does not exist, but based on the AdditionalKDocResolutionProviderForTest,
 * it will be resolved as the above foo().
 */
fun bar() = 3

// REF: (test).foo()