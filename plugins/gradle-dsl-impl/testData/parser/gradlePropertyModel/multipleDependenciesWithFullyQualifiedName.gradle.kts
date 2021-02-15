extra["prop1"] = "value1"
val prop1 = "value2"
val prop2 by extra("${prop1} and ${project.extra["prop1"]}")
