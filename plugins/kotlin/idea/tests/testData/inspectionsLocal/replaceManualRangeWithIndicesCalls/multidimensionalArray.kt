// WITH_STDLIB
// PROBLEM: none
fun foo(matrix: Array<Array<Int>>) {
    for (i in <caret>0 until matrix[0].size) {
        for (j in 0 until matrix.size) {
            print(matrix[j][i])
        }
    }
}