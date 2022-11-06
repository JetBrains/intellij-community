// MODE: function_return

fun foo(x: String?): Int? = x?.let{ it.length }

fun foo2() {
    listOf("a").map {
        println()
        it.length
    }
}

fun foo3() {
    listOf("a").map {
        println()
        (it.length + it.length)
    }
}

fun test(b1: Boolean) {
    val s = run {
        println()
        when {
            b1 -> "b1"
            else -> null
        } ?: ""
    }
}

val s = buildString {
    listOf("").joinTo(this) {
        val x = 42
        if (true) ""
        else
            ""
    }
}

fun <T> lambdaTestOf(body: () -> T) {}

var value: String? = null
val list = listOf<String>()
val test = lambdaTestOf {
    val intermediate = when (val sel = value) {
        "some" -> sel.length
        else -> list.firstOrNull()?.length
    }
    intermediate ?: 8
}