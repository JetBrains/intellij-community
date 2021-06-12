// WITH_RUNTIME
fun main() {
    "abc".<caret>filter { it == 'a' }.any()
}