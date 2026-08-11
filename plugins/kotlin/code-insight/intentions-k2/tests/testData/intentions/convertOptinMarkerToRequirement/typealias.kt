// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET
@RequiresOptIn
annotation class ReqOptinAnnotation

@OptIn(<caret>ReqOptinAnnotation::class)
typealias A = Int
