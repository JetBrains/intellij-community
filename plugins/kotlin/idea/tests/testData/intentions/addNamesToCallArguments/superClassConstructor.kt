// AFTER-WARNING: Parameter 'p1' is never used
// AFTER-WARNING: Parameter 'p2' is never used
open class C(p1: Int, p2: Int)

class D : <caret>C(1, 2)