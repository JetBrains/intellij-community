// "Replace with 'arrayOf'" "true"
annotation class Ann(val x: IntArray = [1, 2, 3]) {
    class Nested {
        val y1: IntArray = [
            1,<caret>
            2, // comment
            3
        ]
    }
}
