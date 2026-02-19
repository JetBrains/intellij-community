interface R<T>

fun <T> R<T>.ext2(): Unit = TODO()

fun Any.ext1(): Unit = TODO()

interface I

fun test(r: R<in I>) {
    r.ex<caret>
}

// ORDER: ext2
// ORDER: ext1
