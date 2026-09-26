// WITH_COROUTINES
// PROBLEM: Getter returns a new 'Job' on each access
// FIX: none

import kotlinx.coroutines.Job

val String.job <caret>get() = Job()
