// REGISTRY: kotlin.analysis.experimentalKDocResolution true
// FIX: none

@Deprecated("", level = DeprecationLevel.WARNING)
fun funWarning() {}

@Deprecated("", level = DeprecationLevel.HIDDEN)
fun funHidden() {}

@Deprecated("", level = DeprecationLevel.ERROR)
fun funError() {}

/**
 * [funWarning] [fun<caret>Hidden] [funError]
 */
fun usage() {}