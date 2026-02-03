
fun Int.foo(): Int {

}

fun Int?.faz(): Int {

}

val a = null.f<caret>

// ABSENT: foo
// ABSENT: faz