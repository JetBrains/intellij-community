package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("kapt")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
