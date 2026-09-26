// WITH_STDLIB
enum class UnreachableForValue(
    val value: UInt,
) {
    A(1u), B(2u);

    companion object {

        const val ALSO_B: UInt = 3u

        fun forValue(value: UInt): UnreachableForValue =
            when (value) {
                A.value -> A
                B.value, ALSO_B -> B
                else -> throw IllegalArgumentException()
            }
        
        fun test(value: UInt) {
            if (value == 1u) {}
        }
    }
}