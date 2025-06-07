// NO_OPTION: SETTER_PARAMETER|Add use-site target 'setparam'
// CHOSEN_OPTION: PROPERTY|Add use-site target 'property'

annotation class A

class Constructor(@A<caret> val foo: String)