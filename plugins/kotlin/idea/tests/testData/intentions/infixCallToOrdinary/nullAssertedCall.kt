// AFTER-WARNING: Unnecessary non-null assertion (!!) on a non-null receiver of type String
// AFTER-WARNING: Parameter 'other' is never used
infix fun String.compareTo(other: String) = 0

fun foo(x: String) {
    x!! <caret>compareTo "1"
}
