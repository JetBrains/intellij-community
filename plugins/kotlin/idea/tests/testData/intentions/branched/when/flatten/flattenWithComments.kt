// IGNORE_K1

fun test(n: Int): String {
    return when(n)<caret> {
        in 0..10 -> "small" //small
        in 10..100 -> "average"
        else -> when(n) {
            in 100..1000 -> "big" // big
            in 1000..10000 -> "very big"
            else -> "unknown"
        } // end
    }
}