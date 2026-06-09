// PROBLEM: Modifiers should be placed directly before the relevant element
annotation class Inject
annotation class VisibleForTesting

class Example {
    <caret>public @set:[Inject VisibleForTesting] var x: String = ""
}
