// "Remove redundant 'if' statement" "true"
operator fun String.not(): Boolean = false

fun bar(value: Int): Boolean {
    <caret>if (!"hello") {
        return false
    }
    else {
        return true
    }
}