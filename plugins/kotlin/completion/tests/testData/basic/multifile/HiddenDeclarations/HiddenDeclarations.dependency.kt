package test

@Deprecated("hidden", level = DeprecationLevel.HIDDEN)
fun hiddenFun(){}

@Deprecated("error", level = DeprecationLevel.ERROR)
fun errorNotHiddenFun(){}

fun notHiddenFun(){}

@Deprecated("hidden", level = DeprecationLevel.HIDDEN)
var hiddenProperty: Int = 1

@Deprecated("error", level = DeprecationLevel.ERROR)
var errorNotHiddenProperty: Int = 1

var notHiddenProperty: Int = 1

@Deprecated("hidden", level = DeprecationLevel.HIDDEN)
val String.hiddenExtension: Int get() = 1

@Deprecated("error", level = DeprecationLevel.ERROR)
val String.errorNotHiddenExtension: Int get() = 1

// ALLOW_AST_ACCESS