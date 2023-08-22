// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface TheInterface {
}

class TheClass : TheInterface {
}

annotation class magnificent
annotation class Deprecated

@Deprecated
@magnificent abstract class AbstractClass<T> {
}
