fun foo(l: Boolean, m: Boolean, r: Boolean) : Boolean {
    return l || (m || r<caret>)
}