package dependency

interface First
interface Second

fun Any.anyExtension() {}
fun First.firstExtension() {}
fun Second.secondExtension() {}