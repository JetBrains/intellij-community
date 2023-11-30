// "Add missing actual declarations" "true"
// IGNORE_K2

import kotlin.random.Random

expect class My<caret>Generator {
    fun generate(): Random
}