// CHOSEN_OPTION: PROPERTY_GETTER
// WITH_STDLIB
// IGNORE_K2
// Issue: KTIJ-32504

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}