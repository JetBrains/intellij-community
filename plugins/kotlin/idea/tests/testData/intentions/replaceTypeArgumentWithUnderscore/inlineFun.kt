// AFTER-WARNING: Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of functional types

abstract class SomeClass<T>
class SomeImplementation : SomeClass<String>()
class OtherImplementation : SomeClass<Int>()

inline fun <S : SomeClass<T>, T> run() {}

fun foo() {
    run<SomeImplementation, <caret>String>()
}