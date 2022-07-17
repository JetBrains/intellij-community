// WITH_STDLIB
// FIX: Replace with loop over elements
fun foo(matrix: Array<Array<Int>>) {
    for (i in 0 until matrix[0].size) {
        for (j in <caret>0 until matrix.size) {
            print(matrix[j][i])
        }
    }
}