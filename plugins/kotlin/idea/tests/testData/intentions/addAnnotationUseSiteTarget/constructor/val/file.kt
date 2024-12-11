// NO_OPTION: FILE
// CHOSEN_OPTION: PROPERTY

annotation class A

class Constructor(@A<caret> val foo: String)