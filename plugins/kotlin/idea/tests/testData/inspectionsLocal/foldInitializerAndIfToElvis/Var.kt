// IGNORE_FE10_BINDING_BY_FIR
fun foo(p: List<String?>, b: Boolean) {
    var v = p[0]
    <caret>if (v == null) return
    if (b) v = null
}