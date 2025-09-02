// CHOSEN_OPTION: Add use-site target 'all'
// COMPILER_ARGUMENTS: -Xannotation-target-all

annotation class A

class Constructor(@A<caret> var foo: String)