plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
}

dependencies {
    testImplementation("junit:junit:4.12")
    implementation(kotlin("stdlib-jre8"))
}
