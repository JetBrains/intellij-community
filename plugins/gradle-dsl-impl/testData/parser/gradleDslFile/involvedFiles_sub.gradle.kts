val childVar1 = 23
val childProp1 by extra(childVar1)
val childProp3 by extra(listOf(parentProperty1, childProp1))
val childVar2 = "value"
