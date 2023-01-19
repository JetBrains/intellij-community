// "Create member function 'Doggo.bark'" "true"
fun iWantLambda(block: () -> Unit) {
    block()
}

class Doggo

fun main() {
    val doggo = Doggo()
    iWantLambda(
        block = doggo::bark<caret>
    )
}