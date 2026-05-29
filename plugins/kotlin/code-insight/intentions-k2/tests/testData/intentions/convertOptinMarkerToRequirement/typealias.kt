// K2_AFTER_ERROR: This annotation is not applicable to target 'typealias'. Applicable targets: class, annotation class, property, field, local variable, value parameter, constructor, function, getter, setter, backing field
@RequiresOptIn
annotation class ReqOptinAnnotation

@OptIn(<caret>ReqOptinAnnotation::class)
typealias A = Int
