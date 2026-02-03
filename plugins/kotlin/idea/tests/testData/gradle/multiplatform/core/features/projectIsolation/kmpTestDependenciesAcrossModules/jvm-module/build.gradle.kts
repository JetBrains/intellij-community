plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":test-utils"))
    implementation(libs.kotlin.test)
    implementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}