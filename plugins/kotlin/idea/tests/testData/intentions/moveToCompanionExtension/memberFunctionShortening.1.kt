package usage

import test.Vector

fun use(vector: Vector): Int = vector.length(3)

fun useQualified(): Int = test.Vector().length(4)

fun reference(vector: Vector): (Int) -> Int = vector::length
