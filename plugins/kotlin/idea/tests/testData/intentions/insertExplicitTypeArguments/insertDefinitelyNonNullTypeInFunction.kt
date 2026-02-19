// IS_APPLICABLE: true

fun <T> foo() {
    <caret>bar({ i: T -> i!! })
}

fun <T> bar(t: T) = t!!