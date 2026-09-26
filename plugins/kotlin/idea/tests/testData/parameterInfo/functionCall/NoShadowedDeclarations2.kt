

fun A.some(s: String) {

}

class A {
    private fun some(s: String) {

    }

    fun usage() {
        // private some shadows extension
        some("lol"<caret>)
    }
}


