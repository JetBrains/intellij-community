// "Change the signature of lambda expression" "true"
// DISABLE_ERRORS

fun main(args: Array<String>) {
    Test<String>().perform("") <caret>{  }
}
