// "Create secondary constructor" "false"
// ERROR: This class does not have a constructor
// K2_ERROR: This type does not have a constructor.
// K2_AFTER_ERROR: This type does not have a constructor.

interface T {

}

class A: T(<caret>1) {

}