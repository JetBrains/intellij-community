// "Add missing actual declarations" "true"

import kotlin.random.Random

expect class My<caret>Generator {
    fun generate(): Random
}