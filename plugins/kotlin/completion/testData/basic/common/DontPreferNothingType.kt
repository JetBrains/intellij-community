
val fooPrefixExpressionA: Nothing = TODO()
val fooPrefixExpressionC: Nothing = TODO()

fun test(fooPrefixExpressionB: String) {
    if (fooPrefix<caret>) {

    }
}

// WITH_ORDER
// EXIST: fooPrefixExpressionB
// EXIST: fooPrefixExpressionA
// EXIST: fooPrefixExpressionC
// NOTHING_ELSE