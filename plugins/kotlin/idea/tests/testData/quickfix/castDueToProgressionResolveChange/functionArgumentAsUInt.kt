// "Cast to 'Iterable<UInt>'" "true"
// WITH_RUNTIME

fun append(x: Any) {}
fun append(xs: Collection<*>) {}

fun invoke() {
    append(1U..5U<caret>)
}