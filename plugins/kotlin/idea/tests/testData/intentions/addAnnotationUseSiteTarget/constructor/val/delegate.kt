// NO_OPTION: PROPERTY_DELEGATE_FIELD
// CHOSEN_OPTION: PROPERTY

annotation class A

class Constructor(@A<caret> val foo: String)