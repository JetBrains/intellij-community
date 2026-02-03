rootProject.name = "kmpShareConfigurationViaBuildSrc"

include("moduleX")
include("moduleY")

gradle.lifecycle.beforeProject {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}