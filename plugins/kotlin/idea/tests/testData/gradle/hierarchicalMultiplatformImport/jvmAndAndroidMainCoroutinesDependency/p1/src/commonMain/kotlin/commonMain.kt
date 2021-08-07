@file:Suppress("unused")

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun commonMainResolved() = GlobalScope.launch(Dispatchers.Main) {

}
