// "Cast to 'Iterable<Int>'" "true"

class SmartList<T> {
    constructor (x: T) {}
    constructor (x: Collection<T>) {}
}

fun invoke() {
    SmartList(1..10<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OverloadResolutionChangeFix