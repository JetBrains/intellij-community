// WITH_COROUTINES

package test

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf

val x = flowOf("1").<caret>filterIsInstance<String>()