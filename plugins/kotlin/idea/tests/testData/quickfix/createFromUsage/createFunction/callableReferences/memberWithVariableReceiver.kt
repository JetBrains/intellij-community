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
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix