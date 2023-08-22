// FIR_COMPARISON
// FIR_IDENTICAL
import lib.JavaClass

class KotlinClass : JavaClass() {
    ove<caret>
}
// ABSENT: { "itemText": "override var value: Int" }