// "Cast to 'Iterable<ULong>'" "true"
// WITH_STDLIB

fun append(x: Any) {}
fun append(xs: Collection<*>) {}

fun invoke() {
    append(1UL..5UL<caret>)
}