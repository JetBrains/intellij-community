import dependency.C

// "Replace with 'newFun(this)'" "true"

fun foo(c: dependency.C) {
    C.<selection><caret></selection>newFun(c)
}