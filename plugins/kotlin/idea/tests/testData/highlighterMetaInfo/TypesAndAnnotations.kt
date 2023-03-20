// FIR_IDENTICAL
interface TheInterface {
}

class TheClass : TheInterface {
}

annotation class magnificent
annotation class Deprecated

@Deprecated
@magnificent abstract class AbstractClass<T> {
}
