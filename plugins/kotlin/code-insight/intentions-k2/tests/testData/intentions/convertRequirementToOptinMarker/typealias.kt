// K2_AFTER_ERROR:
// K2_ERROR: WRONG_ANNOTATION_TARGET
@RequiresOptIn
annotation class ReqOptinAnnotation

@<caret>ReqOptinAnnotation
typealias A = Int
