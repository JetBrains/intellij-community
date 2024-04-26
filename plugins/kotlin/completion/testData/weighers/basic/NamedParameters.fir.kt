// FIR_COMPARISON
package test

import temp.test.*

val listThisFileVal = 12
fun listThisFileFun() = 1

fun listFunNotMatchingType() = ""

class ListThisFileClass {}

fun test(listParam: Int) {
    val listLocalVal = 12
    Options(list<caret>)
}

// ORDER: listLocalVal
// ORDER: listParam
// ORDER: listThisFileVal
// ORDER: listThisFileFun
// ORDER: listImportedVal
// ORDER: listImportedFun
// ORDER: "listMatch ="
// ORDER: "listNew ="
// ORDER: listFunNotMatchingType

