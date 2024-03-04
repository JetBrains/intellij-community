// WITH_STDLIB
fun <T> example(x: T, block: (T) -> Unit, nullable: (T?) -> Unit, other: (Any?) -> Unit) {
    val b = x == null
    // Avoid 'always null' warning, as possible replacement like 'null' or 'null as T' produce other warnings or errors
    if (b) block(x)

    if (b) nullable(<weak_warning descr="Value of 'x' is always null">x</weak_warning>)

    if (b) other(<weak_warning descr="Value of 'x' is always null">x</weak_warning>)
}