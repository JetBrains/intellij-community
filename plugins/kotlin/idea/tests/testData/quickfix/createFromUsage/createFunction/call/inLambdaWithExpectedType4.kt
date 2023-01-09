// "Create function 'createMe'" "true"
fun param(p: () -> Boolean, x: Int) {
}

fun use(){
    param({ <caret>createMe() }, 1)
}
