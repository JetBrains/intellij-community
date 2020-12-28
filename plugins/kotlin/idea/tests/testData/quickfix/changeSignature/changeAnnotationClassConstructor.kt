// "Add parameter to constructor 'AnnParClass'" "true"

private annotation class AnnParClass(val p1: Int, val p2: Int)
@AnnParClass(1, 2, <caret>3)
private val vac = 3
