// FIR_IDENTICAL
// FIR_COMPARISON
class JustClass<T>(val p: T)

fun <T, U> JustClass<T>.chainExt(p: U): JustClass<U> = TODO()

// T - inferrable from 'JustClass<T>' => (T)
// U - inferrable from 'p' => (T, U)

fun test() {
    val chainExt: JustClass<String> = JustClass(1).chainExt("").<caret>
}

// ELEMENT: p