inline fun <reified R> Iterable<*>.myFilterIsInstance(): List<R> {
    return filterIsInstanceTo(ArrayList<R>())
}

fun foo(list: List<Any>) {
    list.<caret>
}

// ELEMENT: myFilterIsInstance
