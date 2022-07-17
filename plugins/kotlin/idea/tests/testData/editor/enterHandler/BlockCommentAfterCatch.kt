class A {
    fun b() {
        try {

        } catch(r: Exception) {
            /* hi */ <caret>
            println("hi")
        }
    }
}
