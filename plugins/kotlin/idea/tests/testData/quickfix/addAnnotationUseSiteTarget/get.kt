// "Add use-site target" "true"
// CHOSEN_OPTION: PROPERTY_GETTER|Add use-site target 'get'

@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno2

@<caret>Anno2
var b = 42

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationUseSiteTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$ChooseAnnotationUseSiteTargetFix