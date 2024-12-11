// NO_OPTION: SETTER_PARAMETER
// CHOSEN_OPTION: PROPERTY

annotation class A

class Constructor(@A<caret> val foo: String)