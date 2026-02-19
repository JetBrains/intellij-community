class JustClass<T>(val p: T)

fun <T, U> JustClass<Int>.chainExt(p: T): JustClass<U> = TODO()

// T - inferrable from 'p' => (T)
// U - cannot be inferred (return value type is not taken into consideration)

fun test() {
    val chainExt: JustClass<String> = JustClass(1).chainExt("").<caret>
}

// ELEMENT: p