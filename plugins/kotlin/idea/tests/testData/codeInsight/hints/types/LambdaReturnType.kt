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
    val s/*<# : |[kotlin.String:kotlin.fqn.class]String #>*/ = run {
        println()
        when {
            b1 -> "b1"
            else -> null
        } ?: ""
    }
}

val s/*<# : |[kotlin.String:kotlin.fqn.class]String #>*/ = buildString {
    listOf("").joinTo(this) {
        val x = 42
        if (true) ""
        else
            ""
    }
}

fun <T> lambdaTestOf(body: () -> T) {}

var value: String? = null
val list/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[kotlin.String:kotlin.fqn.class]String|> #>*/ = listOf<String>()
val test/*<# : |[kotlin.Unit:kotlin.fqn.class]Unit #>*/ = lambdaTestOf {
    val intermediate/*<# : |[kotlin.Int:kotlin.fqn.class]Int|? #>*/ = when (val sel/*<# : |[kotlin.String:kotlin.fqn.class]String|? #>*/ = value) {
        "some" -> sel.length
        else -> list.firstOrNull()?.length
    }
    intermediate ?: 8
}