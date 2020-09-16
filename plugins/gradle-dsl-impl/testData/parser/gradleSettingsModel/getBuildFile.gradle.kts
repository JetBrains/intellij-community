include("app", ":lib")
project(":lib").buildFileName = "test.gradle.kts"
include("olib")
project(":olib").projectDir = File(rootDir, "otherlibs/xyz")
project(":olib").buildFileName = "other.gradle.kts"