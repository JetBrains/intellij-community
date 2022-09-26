fun <T: Number> create(param: List<T> = listOf(1 as T)): List<T> = TODO()

// T - not inferrable from 'param' (argument is absent, default value is not analysed)

fun test() {
    val list: List<Int> = create().<caret>
}

// ELEMENT: subList