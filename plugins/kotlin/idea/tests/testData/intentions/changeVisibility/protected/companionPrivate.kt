open class A {
    companion object {
        <caret>private val myMap: Map<String, String> = mapOf("1" to "2")
    }
}
