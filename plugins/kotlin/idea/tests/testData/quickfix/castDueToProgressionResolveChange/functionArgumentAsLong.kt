// "Cast to 'Iterable<Long>'" "true"

fun append(x: Any) {}
fun append(xs: Collection<*>) {}

fun invoke() {
    append(1L..10L<caret>)
}