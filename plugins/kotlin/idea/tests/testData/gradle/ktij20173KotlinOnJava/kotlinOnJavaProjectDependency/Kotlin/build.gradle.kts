plugins {
    kotlin("jvm") version "{{kgp_version}}"
}

dependencies {
    testImplementation(project(path = ":JavaOnly", configuration = "default"))
}