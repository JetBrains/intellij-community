// "Create secondary constructor" "false"
// ERROR: This class does not have a constructor
// K2_AFTER_ERROR: NO_CONSTRUCTOR
// K2_ERROR: NO_CONSTRUCTOR

interface T {

}

class A: T(<caret>1) {

}