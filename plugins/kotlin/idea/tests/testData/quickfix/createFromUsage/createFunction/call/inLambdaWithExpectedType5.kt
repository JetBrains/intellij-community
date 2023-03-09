// "Create function 'createMe'" "true"
fun param(p: () -> Boolean) {
}

fun use(){
    param {
        <caret>createMe()
        true
    }
}
