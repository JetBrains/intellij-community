// AFTER-WARNING: Identity equality for arguments of types Int and Int is deprecated
fun neq(a: Int, b: Int) {
    if (a <caret>!== b) {}
}