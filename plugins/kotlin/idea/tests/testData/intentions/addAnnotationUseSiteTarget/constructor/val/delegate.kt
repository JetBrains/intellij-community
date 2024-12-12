// NO_OPTION: PROPERTY_DELEGATE_FIELD
// CHOSEN_OPTION: PROPERTY
// IGNORE_K2
// Issue: KTIJ-32504

annotation class A

class Constructor(@A<caret> val foo: String)