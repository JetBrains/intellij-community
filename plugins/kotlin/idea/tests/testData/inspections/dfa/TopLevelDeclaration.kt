// WITH_STDLIB
val percentage: Double = 103.0

val formattedPercentage: Int = when {
    percentage >= 10 -> 1
    <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
    else -> 3
}