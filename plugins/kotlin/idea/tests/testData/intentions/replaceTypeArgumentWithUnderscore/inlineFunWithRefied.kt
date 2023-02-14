abstract class SomeClass<T>
class SomeImplementation : SomeClass<String>()
class OtherImplementation : SomeClass<Int>()

inline fun <reified S : SomeClass<T>, T> run() {}

fun foo() {
    run<OtherImplementation, <caret>Int>()
}