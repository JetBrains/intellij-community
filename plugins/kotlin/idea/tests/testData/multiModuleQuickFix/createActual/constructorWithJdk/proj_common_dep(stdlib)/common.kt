// "Add missing actual declarations" "true"

import kotlin.random.Random

expect class My<caret>Generator(r: Random) {
    val i: Int
}