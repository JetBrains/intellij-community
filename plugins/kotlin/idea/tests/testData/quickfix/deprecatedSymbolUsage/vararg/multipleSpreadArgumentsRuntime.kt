// "Replace with 'newFun(*p, 1)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(*p, 1)"))
fun oldFun(vararg p: Int){
    newFun(*p, 1)
}

fun newFun(vararg p: Int){}

fun foo(list1: List<Int>,list2: List<Int>) {
    <caret>oldFun(*list1.toIntArray(), 0, *list2.toIntArray())
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix