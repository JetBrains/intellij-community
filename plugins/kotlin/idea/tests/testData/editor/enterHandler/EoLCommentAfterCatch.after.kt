class A {
    fun b() {
        try {

        } catch(r: Exception) {
            // line comment
            <caret>
            println("hi")
        }
    }
}
