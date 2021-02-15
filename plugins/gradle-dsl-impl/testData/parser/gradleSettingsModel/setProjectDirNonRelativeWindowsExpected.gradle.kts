include(":app")
include(":lib")
project(":lib").projectDir = File(rootDir, "mylibrary")
rootProject.name="My Application"
project(":app").projectDir = file("C:\\cool\\app")
