// "Make 'Owner' 'abstract'" "false"
// ERROR: Abstract property 'x' in non-abstract class 'Companion'
// ACTION: Make 'x' not abstract
// ACTION: Make internal
// K2_AFTER_ERROR: ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS
// K2_ERROR: ABSTRACT_PROPERTY_IN_NON_ABSTRACT_CLASS

class Owner {
    companion object {
        <caret>abstract val x: Int
    }
}
