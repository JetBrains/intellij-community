// "Move annotation to receiver type" "false"
// ERROR: This annotation is not applicable to target 'declaration' and use site target '@receiver'
// K2_AFTER_ERROR: This annotation is not applicable to target 'declaration' and use-site target '@receiver'. Applicable targets: class, annotation class, property, field, local variable, value parameter, constructor, function, getter, setter, backing field
// ACTION: Add annotation target
// ACTION: Introduce import alias
// ACTION: Make internal
// ACTION: Make private

annotation class Ann

@receiver:Ann<caret>
fun foo() {
}