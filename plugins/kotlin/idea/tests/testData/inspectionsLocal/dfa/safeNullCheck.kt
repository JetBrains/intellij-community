// PROBLEM: none
fun test(x: X, kls: Kls?) {
    if (x == kls?.x) return
    if (<caret>kls != null) {
        if (x != kls.x) {}
    }
}

class Kls(val x: X)
class X
