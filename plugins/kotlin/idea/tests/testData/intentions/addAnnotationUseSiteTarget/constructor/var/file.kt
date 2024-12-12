// NO_OPTION: FILE
// CHOSEN_OPTION: PROPERTY
// IGNORE_K2
// Issue: KTIJ-32504

annotation class A

class Constructor(@A<caret> var foo: String)