// NO_OPTION: PROPERTY_SETTER
// CHOSEN_OPTION: PROPERTY

annotation class A

class Constructor(@A<caret> val foo: String)