// FIR_COMPARISON
fun <T> Iterable<T>.first() : T? {
    return this.iterator()?.next()
}

fun main(args : Array<String>) {
    val test = ArrayList<Int>() // aliased in JVM to java.util.ArrayList
    test.<caret>
}

// EXIST: first
