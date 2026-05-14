// "Replace with 's.extension().newFun()'" "true"

import dependency.newFun
import dependency2.extension

fun foo() {
    "a".extension().<selection><caret></selection>newFun()
}