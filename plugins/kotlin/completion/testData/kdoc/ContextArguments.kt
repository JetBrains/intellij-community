// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String)
fun fooString() {}

context(_: Int)
fun fooInt() {}

/**
 * [foo<caret>]
 */
fun documented() {}

// EXIST: fooString
// EXIST: fooInt
