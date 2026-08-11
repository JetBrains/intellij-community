// "Propagate 'TopMarker' opt-in requirement to 'topUserVal'" "true"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce import alias
// ACTION: Opt in for 'TopMarker' in containing file 'annotationInTopLevelProperty.kts'
// ACTION: Opt in for 'TopMarker' in module 'light_idea_test_case'
// ACTION: Opt in for 'TopMarker' on 'topUserVal'
// ACTION: Propagate 'TopMarker' opt-in requirement to 'topUserVal'
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR

@RequiresOptIn
annotation class TopMarker

@TopMarker
class TopClass

@Target(AnnotationTarget.TYPE)
@TopMarker
annotation class TopAnn

val topUserVal: @<caret>TopAnn TopClass? = null

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix