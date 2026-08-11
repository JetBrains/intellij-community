// "Add use-site target" "true"
// CHOSEN_OPTION: ALL|Add use-site target 'all'
// COMPILER_ARGUMENTS: -Xannotation-target-all
// K2_ERROR: WRONG_ANNOTATION_TARGET


@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Anno

@<caret>Anno
val b = 42

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationUseSiteTargetFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongAnnotationTargetFixFactories$ChooseAnnotationUseSiteTargetFix