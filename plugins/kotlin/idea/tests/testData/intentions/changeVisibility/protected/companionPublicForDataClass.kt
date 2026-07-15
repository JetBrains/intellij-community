// IS_APPLICABLE: false
data class A(val x: Int) {
    companion object {
        <caret>val myMap: Map<String, String> = mapOf("1" to "2")
    }
}
