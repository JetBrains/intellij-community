// NO_OPTION: FILE
// CHOSEN_OPTION: PROPERTY
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}