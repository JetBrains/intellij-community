// FIR_COMPARISON
package test

@Deprecated("hidden", level = DeprecationLevel.HIDDEN)
fun hiddenFunFromSameFile(){}

@Deprecated("error", level = DeprecationLevel.ERROR)
fun errorNotHiddenFunFromSameFile(){}

fun String.foo() {
    hid<caret>
}

// ABSENT: hiddenFun
// ABSENT: hiddenProperty
// ABSENT: hiddenFunFromSameFile
// ABSENT: hiddenExtension
// EXIST: notHiddenFun
// EXIST: notHiddenProperty
// EXIST: errorNotHiddenFunFromSameFile
// EXIST: errorNotHiddenFun
// EXIST: errorNotHiddenProperty
// EXIST: errorNotHiddenExtension
