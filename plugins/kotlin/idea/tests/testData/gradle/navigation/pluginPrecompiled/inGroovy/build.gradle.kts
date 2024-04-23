plugins {
    id("<caret>my-plugin")
}

tasks.named("myPluginTask") {
    doLast{
        println("The task from plugin is available in the build script")
    }
}
