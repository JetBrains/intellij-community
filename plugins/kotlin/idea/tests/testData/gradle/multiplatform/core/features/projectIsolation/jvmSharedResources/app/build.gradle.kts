plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(project(":service"))
}

application {
    mainClass.set("com.example.AppMain")
}

tasks.named("build") {
    dependsOn(":common:build")
    doLast {
        println("App build depends on common build.")
    }
}
