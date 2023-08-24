// "Change the signature of lambda expression" "true"

fun foo(f: Int.(Int, Int) -> Int) {

}

fun test() {
    foo { <caret>a: Int -> 0 }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionLiteralSignatureFix