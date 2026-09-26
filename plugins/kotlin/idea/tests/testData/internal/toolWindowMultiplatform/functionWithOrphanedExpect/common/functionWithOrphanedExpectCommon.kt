package functionWithOrphanedExpect

fun myFunction(x: Int): String = myDependency(x + 1)

expect fun myDependency(x: Int): String
