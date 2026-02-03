package target

import source.SourceObject.other
import source.sourcePackFun

fun targetPackFun(){}


fun foo() {
    other()
    sourcePackFun()
    targetPackFun()
    bar++
}

var bar = 1
