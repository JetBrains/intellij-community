extra["newProp"] = 123
extra["prop1"] = extra["newProp"] as Int
val prop2 by extra(extra["newProp"] as Int)
