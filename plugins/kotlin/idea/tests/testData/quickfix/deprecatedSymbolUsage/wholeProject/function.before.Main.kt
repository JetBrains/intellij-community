// "Replace usages of 'oldFun(Int): Unit' in whole project" "true"
// K2_ACTION: "Replace usages of 'oldFun(Int)' in whole project" "true"

import pack.oldFun

fun foo() {
    <caret>oldFun(0)
    oldFun(2)
}

fun bar() {
    oldFun(3)
}
