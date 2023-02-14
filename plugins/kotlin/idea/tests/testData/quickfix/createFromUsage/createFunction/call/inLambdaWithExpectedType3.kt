// "Create function 'createMe'" "true"
fun param(p: (i: Int, s: String) -> Boolean) {
}

fun use(){
    param { i, s ->
        <caret>createMe(i, s)
    }
}
