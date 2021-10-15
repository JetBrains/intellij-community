// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

@<caret>Experimental(Experimental.Level.WARNING)
annotation class Marker
