// "Replace usages of 'oldFun(Int): Unit' in whole project" "true"
// K2_ACTION: "Replace usages of 'oldFun(Int)' in whole project" "true"

import newPack.newFun

fun foo() {
    <caret>newFun(0 + 1)
    newFun(2 + 1)
}

fun bar() {
    newFun(3 + 1)
}
