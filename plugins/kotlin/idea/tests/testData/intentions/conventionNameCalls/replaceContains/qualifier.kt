// IGNORE_FE10_BINDING_BY_FIR
// AFTER-WARNING: Parameter 's' is never used
class C {
    companion object {
        operator fun contains(s: String) = true
    }
}

fun foo() {
    C.<caret>contains("x")
}
