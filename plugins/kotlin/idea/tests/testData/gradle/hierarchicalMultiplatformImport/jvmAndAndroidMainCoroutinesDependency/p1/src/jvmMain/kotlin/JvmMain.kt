@file:Suppress("unused")

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun jvmMain() = MainScope().launch(Dispatchers.IO) {

}
