plugins {
    id("buildsrc.convention.kotlin-kapt")
}

dependencies {
    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
    testImplementation(libs.junit)
}
