class JustClass<T>(val p: T)

fun <T, U> JustClass<T>.chainExt(p: U): JustClass<U> = TODO()
fun test() {
    val chainExt: JustClass<String> = JustClass(1).chainExt("").<caret>
}

// ELEMENT: p