// "Cast to 'Iterable<Char>'" "true"

fun append(x: Any) {}
fun append(xs: Collection<*>) {}

fun invoke() {
    append('a'..'c'<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OverloadResolutionChangeFix