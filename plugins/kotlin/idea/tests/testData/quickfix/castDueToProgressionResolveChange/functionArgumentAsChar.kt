// "Cast to 'Iterable<Char>'" "true"

fun append(x: Any) {}
fun append(xs: Collection<*>) {}

fun invoke() {
    append('a'..'c'<caret>)
}