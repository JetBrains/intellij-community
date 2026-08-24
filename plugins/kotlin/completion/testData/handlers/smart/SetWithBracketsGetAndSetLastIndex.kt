class Matrix {
    operator fun get(x: Int, y: Int): String = ""
    operator fun set(x: Int, y: Int, value: String) {}
}

fun usage(matrix: Matrix, idx: Int) {
    matrix[0, <caret>] = ""
}

// ELEMENT: idx
