// WITH_STDLIB
fun <T> example(x: T, block: (T) -> Unit, nullable: (T?) -> Unit, other: (Any?) -> Unit) {
    // Avoid 'always null' warning, as possible replacement like 'null' or 'null as T' produce other warnings or errors
    if (x == null) block(x)

    if (x == null) nullable(<weak_warning descr="Value of 'x' is always null">x</weak_warning>)

    if (x == null) other(<weak_warning descr="Value of 'x' is always null">x</weak_warning>)
}