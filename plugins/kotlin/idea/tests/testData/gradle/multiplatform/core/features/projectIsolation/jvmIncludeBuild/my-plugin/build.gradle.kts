plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

group = "org.example.my-plugin"
version = "1.0"

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.example.my-plugin"
            implementationClass = "org.example.myplugin.MyPlugin"
        }
    }
}


