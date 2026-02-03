plugins {
    kotlin("jvm")
    `java-test-fixtures`
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}