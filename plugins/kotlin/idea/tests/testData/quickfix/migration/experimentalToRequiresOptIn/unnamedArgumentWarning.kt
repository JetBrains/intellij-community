// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB 1.7.0

@<caret>Experimental(Experimental.Level.WARNING)
annotation class Marker
