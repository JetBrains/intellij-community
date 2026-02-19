// WITH_STDLIB
object BuildConfig {
    const val IS_PRODUCTION_BUILD = true
    const val IS_DEVELOPMENT_BUILD = !IS_PRODUCTION_BUILD
}

fun main(x: Int) {
    val e0 = if (BuildConfig.IS_PRODUCTION_BUILD) 0 else 1
    val e1 = if (!BuildConfig.IS_PRODUCTION_BUILD) 1 else 2
    val e2 = if (BuildConfig.IS_PRODUCTION_BUILD || x > 1) 3 else 4
    println(e0 + e1 + e2)
}