class Matrix {
    operator fun get(x: Int, y: Int, z: Int): String = ""
}

fun elem(): Int = 0

fun usage(matrix: Matrix) {
    matrix[
        elem(),
        elem(),
        e<caret>,
    ]
}

// ELEMENT: elem
// TAIL_TEXT: () (<root>)
