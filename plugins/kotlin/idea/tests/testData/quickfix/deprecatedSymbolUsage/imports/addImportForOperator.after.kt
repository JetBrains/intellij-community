// "Replace with 'newFun(n + listOf(s))'" "true"

import declaration.listOf
import declaration.newFun
import weird.collections.plus

fun foo() {
    <selection><caret></selection>newFun(listOf(2) + listOf(1))
}
