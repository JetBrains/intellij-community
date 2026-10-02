package usage

import test.Vector

fun use(vector: Vector): Int = vector.length

fun useQualified(): Int = test.Vector().length
