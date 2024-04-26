plugins {
    `java-library`
}

repositories {
    jcenter()
}

dependencies {
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    api(libs.commons.math3)
    implementation(libs.guava)
}

tasks.test {
    useJUnitPlatform()
}
