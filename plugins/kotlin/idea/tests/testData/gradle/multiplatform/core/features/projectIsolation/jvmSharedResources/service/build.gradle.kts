plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":common"))
}

tasks.named("build") {
    dependsOn(":common:prepareResources")

    doLast {
        println("Building 'service' module...")
    }
}

