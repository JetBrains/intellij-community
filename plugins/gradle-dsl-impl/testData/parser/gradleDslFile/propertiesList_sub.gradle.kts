// why childVar2 before childVar1?  The test related to this file has
// strong assertions about the ordering of properties, and the Groovy
// equivalent has variables within ext { } blocks -- which does not
// have an equivalent in Kotlinscript, but the test asserts the order.
val childVar2 = "value"
val childVar1 = 23
val childProp1 by extra(childVar1)
val childProp3 by extra(listOf(rootProject.extra["parentProperty1"], extra["childProp1"]))
