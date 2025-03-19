fun returnFun(): Int = 10
fun <T> returnAnything(): T = null!!

fun usage(a: Int?): Int {
    a ?: re<caret>
    return 10
}

// function of the same type as `a` is preferred to return in this case

// IGNORE_K2
// ORDER: returnFun
// ORDER: return
// ORDER: returnAnything