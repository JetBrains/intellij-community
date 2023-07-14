// "Add 'kotlin.Any' as upper bound for E" "true"

class A<T : Any>
fun <E> bar(x: A<E<caret>>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddGenericUpperBoundFix