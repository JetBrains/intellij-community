// K2_ERROR: DATA_CLASS_CONSISTENT_COPY_WRONG_ANNOTATION_TARGET

// "Remove annotation" "true"
@ConsistentCopyVisibility<caret>
class Foo private constructor(val x: Int)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix