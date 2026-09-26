class Matrix {
    operator fun get(x: Int, y: Int): String = ""
}

fun index(): Int = 0

fun usage(matrix: Matrix) {
    matrix[inde<caret>, index()]
}

// ELEMENT: index
// TAIL_TEXT: () (<root>)
