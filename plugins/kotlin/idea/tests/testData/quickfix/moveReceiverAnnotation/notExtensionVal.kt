// "Move annotation to receiver type" "false"
// ERROR: This annotation is not applicable to target 'declaration' and use site target '@receiver'
// ACTION: Add annotation target
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Introduce import alias
// ACTION: Make internal
// ACTION: Make private
// ACTION: Specify type explicitly
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
// K2_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET

annotation class Ann

@receiver:Ann<caret>
val bar get() = ""