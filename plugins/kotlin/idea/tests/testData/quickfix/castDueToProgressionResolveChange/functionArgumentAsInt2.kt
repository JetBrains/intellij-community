// "Cast to 'Iterable<Int>'" "true"

fun append(y: String, x: Any, z: Int) {}
fun append(y: String, xs: Collection<*>, z: Int) {}

fun invoke() {
    append("", 1..10<caret>, 0)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OverloadResolutionChangeFix