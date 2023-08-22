// WITH_STDLIB
fun List<Any>.test() {
    <caret>filter { it is String }
}