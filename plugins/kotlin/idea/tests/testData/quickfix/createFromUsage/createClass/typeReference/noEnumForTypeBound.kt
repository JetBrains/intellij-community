// "Create enum 'NotExistent'" "false"
// ERROR: Unresolved reference: NotExistent
// K2_AFTER_ERROR: Unresolved reference 'NotExistent'.
class TPB<X : <caret>NotExistent>