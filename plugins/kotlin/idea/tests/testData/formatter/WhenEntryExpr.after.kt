fun some(x: Any) {
    when (x) {
        is Int ->
            0

        3 ->
            2

        in 0..3 ->
            2

        else ->
            1
    }
    when (x) {
        is Int -> {
            0
        }

        3 -> {
            2
        }

        in 0..3 -> {
            2
        }

        else -> {
            1
        }
    }
    when (x) {
        is Int -> {
            0
        }

        3 -> {
            2
        }

        in 0..3 -> {
            2
        }

        else -> {
            1
        }
    }
    when (x) {
        is
        Int,
            -> {
            0
        }

        3,
            -> {
            2
        }

        in
        0..3,
            -> {
            2
        }

        else
            -> {
            1
        }
    }
    when (x) {
        is
        Int,
            -> listOf(
            0
        )

        3,
            -> if (true)
            listOf(4)
        else
            listOf(
                5
            )

        in
        0..3,
            -> run {
            listOf(2)
        }

        else
            -> when {
            true -> listOf(1)
            false -> listOf(2)
        }
    }
}
// SET_TRUE: ALLOW_TRAILING_COMMA
