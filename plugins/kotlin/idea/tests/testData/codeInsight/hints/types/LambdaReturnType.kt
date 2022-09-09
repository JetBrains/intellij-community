// MODE: function_return

fun foo(x: String?): Int? = x?.let{ it.length<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:28460]Int] #> }

fun foo2() {
    listOf("a").map {
        println()
        it.length<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:28460]Int] #>
    }
}

fun foo3() {
    listOf("a").map {
        println()
        (it.length + it.length)<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:28460]Int] #>
    }
}

fun test(b1: Boolean) {
    val s = run {
        println()
        when {
            b1 -> "b1"<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/String.kt:618]String] #>
            else -> null<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/String.kt:618]String] #>
        } ?: ""<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/String.kt:618]String] #>
    }
}

val s = buildString {
    listOf("").joinTo(this) {
        val x = 42
        if (true) ""<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/CharSequence.kt:618]CharSequence] #>
        else
        ""<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/CharSequence.kt:618]CharSequence] #>
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
    intermediate ?: 8<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:28460]Int] #>
}