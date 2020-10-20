class JustClass<T>(val p: T)

fun <T, U> JustClass<Int>.chainExt(p: T): JustClass<U> = TODO()
fun test() {
    val chainExt: JustClass<String> = JustClass(1).chainExt("").<caret>
}

// ELEMENT: p