// IGNORE_FE10_BINDING_BY_FIR
// AFTER-WARNING: Parameter 'ignoreCase' is never used
// AFTER-WARNING: Parameter 'other' is never used
@Suppress("INAPPLICABLE_OPERATOR_MODIFIER")
public operator fun CharSequence.contains(other: CharSequence, ignoreCase: Boolean = false): Boolean = false
fun test() {
    val foo = "foo"
    foo.c<caret>ontains("bar")
}
