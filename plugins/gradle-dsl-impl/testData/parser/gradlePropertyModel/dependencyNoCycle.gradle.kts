val prop1 by extra("Value")
val prop2 by extra("c${prop1}")
extra["prop1"] = "b${prop2}"
extra["prop2"] = "a${prop1}"
