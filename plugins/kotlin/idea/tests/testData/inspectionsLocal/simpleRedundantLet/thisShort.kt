// WITH_STDLIB


fun Int.foo(): Int {
    return <caret>let { it.hashCode() }
}