// "Add use-site target" "true"
// CHOSEN_OPTION: PROPERTY_GETTER|Add use-site target 'get'
// K2_ERROR: This annotation is not applicable to target 'top level property with backing field'. Applicable targets: getter, setter

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno2

@<caret>Anno2
var b = 42

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationUseSiteTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$ChooseAnnotationUseSiteTargetFix