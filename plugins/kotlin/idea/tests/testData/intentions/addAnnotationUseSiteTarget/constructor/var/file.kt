// NO_OPTION: FILE|Add use-site target 'file'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Constructor(@A<caret> var foo: String)