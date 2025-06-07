// PROBLEM: none
// AFTER-WARNING: Identity equality for arguments of types Float and Float is deprecated
fun foo(y: Boolean) {
    0.0f <caret>=== -0.0f
}