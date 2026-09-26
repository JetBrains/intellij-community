class Matrix {
    operator fun get(x: Int, y: Int, z: Int): String = ""
}

fun index(): Int = 0

fun usage(matrix: Matrix) {
    matrix[
        index(),
        inde<caret>,
        index(),
    ]
}

// ELEMENT: index
// TAIL_TEXT: () (<root>)
