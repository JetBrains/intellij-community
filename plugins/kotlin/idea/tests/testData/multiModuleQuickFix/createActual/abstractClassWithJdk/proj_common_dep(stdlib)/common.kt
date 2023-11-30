// "Add missing actual declarations" "true"
// IGNORE_K2

import kotlin.random.Random

expect abstract class My<caret>Generator {
    abstract fun generate(): Random
}