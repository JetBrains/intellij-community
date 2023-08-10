// FIR_IDENTICAL
// FIR_COMPARISON
fun <T: Number> create(param: List<T> = listOf(1 as T)): List<T> = TODO()

// T - inferrable from 'param' argument

fun test() {
    val list: List<Int> = create(listOf(1)).<caret>
}

// ELEMENT: subList