// NO_OPTION: RECEIVER|Add use-site target 'receiver'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Constructor(@A<caret> val foo: String)