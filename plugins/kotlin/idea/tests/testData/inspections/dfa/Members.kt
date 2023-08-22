// WITH_STDLIB
val percentage: Double = Math.abs(103.0)

class A(val formattedPercentage5: Int = when {
    percentage >= 10 -> 1
    <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
    else -> 3
}) {
    val percentage: Double = Math.abs(103.0)

    init {
        val <warning descr="[UNUSED_VARIABLE] Variable 'b' is never used">b</warning> = when {
            percentage >= 10 -> 1
            <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
            else -> 3
        }
    }

    fun t(): Int {
        return when {
            percentage >= 10 -> 1
            <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
            else -> 3
        }
    }

    val formattedPercentage: Int
        get() = when {
            percentage >= 10 -> 1
            <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
            else -> 3
        }

    val formattedPercentage2: Int = when {
        percentage >= 10 -> 1
        <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
        else -> 3
    }

    val formattedPercentage3: Int by lazy {
        when {
            percentage >= 10 -> 1
            <warning descr="Condition 'percentage >= 100' is always false">percentage >= 100</warning> -> 2
            else -> 3
        }
    }
}