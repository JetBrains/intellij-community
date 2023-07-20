// "Add missing actual declarations" "true"

import kotlin.random.Random

expect abstract class My<caret>Generator {
    abstract fun generate(): Random
}