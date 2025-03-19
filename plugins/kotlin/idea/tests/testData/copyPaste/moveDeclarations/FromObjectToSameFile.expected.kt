package source

import source.SourceObject.other

fun sourcePackFun(){}

object SourceObject {


    fun other() {
        foo()
    }
}


fun foo() {
    other()
    sourcePackFun()
    bar++
}

var bar = 1

