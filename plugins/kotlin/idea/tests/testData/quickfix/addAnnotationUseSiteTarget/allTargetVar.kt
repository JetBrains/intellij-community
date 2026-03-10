// "Add use-site target" "true"
// CHOSEN_OPTION: ALL|Add use-site target 'all'
// COMPILER_ARGUMENTS: -Xannotation-target-all
// IGNORE_K1
// K2_ERROR: This annotation is not applicable to target 'top level property with backing field'. Applicable targets: getter, setter

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno

@<caret>Anno
var b = 42

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationUseSiteTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$ChooseAnnotationUseSiteTargetFix