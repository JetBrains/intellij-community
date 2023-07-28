// "Propagate 'TopMarker' opt-in requirement to 'topUserVal'" "true"
// ACTION: Add '-opt-in=AnnotationInTopLevelProperty.TopMarker' to module light_idea_test_case compiler arguments
// ACTION: Add full qualifier
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce import alias
// ACTION: Opt in for 'TopMarker' in containing file 'annotationInTopLevelProperty.kts'
// ACTION: Opt in for 'TopMarker' on 'topUserVal'
// ACTION: Propagate 'TopMarker' opt-in requirement to 'topUserVal'
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
annotation class TopMarker

@TopMarker
class TopClass

@Target(AnnotationTarget.TYPE)
@TopMarker
annotation class TopAnn

val topUserVal: @<caret>TopAnn TopClass? = null

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix