// K2_AFTER_ERROR: This annotation is not applicable to target 'file' and use-site target '@file'. Applicable targets: class, annotation class, property, field, local variable, value parameter, constructor, function, getter, setter, backing field
// IS_APPLICABLE: false
@file:OptIn(<caret>ReqOptinAnnotation::class)

@RequiresOptIn
annotation class ReqOptinAnnotation

