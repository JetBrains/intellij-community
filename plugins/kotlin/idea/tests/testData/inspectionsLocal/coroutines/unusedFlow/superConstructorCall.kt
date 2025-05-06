// PROBLEM: none
// WITH_COROUTINES
// IGNORE_K1

import kotlinx.coroutines.flow.Flow

class C(flow: Flow<Int>) : B(alsoFlow<caret> = flow)

open class B(alsoFlow: Flow<Int>)
