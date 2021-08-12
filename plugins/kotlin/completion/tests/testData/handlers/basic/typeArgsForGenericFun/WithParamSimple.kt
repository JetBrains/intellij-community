// FIR_COMPARISON
fun <T> create(param: List<T>): List<T> = param

// T - inferrable from 'param'

fun test() {
    create(listOf("1")).<caret>
}

// ELEMENT: subList