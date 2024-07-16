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
// EXIST: {lookupString:"notHiddenFun",attributes:""}
// EXIST: {lookupString:"notHiddenProperty",attributes:""}
// EXIST: {lookupString:"errorNotHiddenFunFromSameFile",attributes:"strikeout"}
// EXIST: {lookupString:"errorNotHiddenFun",attributes:"strikeout"}
// EXIST: {lookupString:"errorNotHiddenProperty",attributes:"strikeout"}
// EXIST: {lookupString:"errorNotHiddenExtension",attributes:"bold strikeout"}
