// "Terminate preceding call with semicolon" "true"

fun foo() {}

fun test {
    { { { foo() } } }()()()
    // comment and formatting
    {<caret>}
}
