// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// ERROR: Using 'Experimental' is an error. Please use RequiresOptIn instead.
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB 1.7.0

import kotlin.Experimental.*

@Experimental<caret>(Level.ERROR)
annotation class Marker
