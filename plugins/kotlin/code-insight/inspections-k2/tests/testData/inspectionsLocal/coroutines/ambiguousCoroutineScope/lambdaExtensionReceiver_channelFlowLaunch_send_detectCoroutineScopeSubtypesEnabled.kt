// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

val myFlow = channelFlow<Int> {
    launch {
        <caret>send(3)
    }
}
