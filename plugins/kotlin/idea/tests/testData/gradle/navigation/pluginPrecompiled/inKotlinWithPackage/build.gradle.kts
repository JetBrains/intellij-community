plugins {
    id("<caret>com.example.my-plugin")
}

tasks.named("myPluginTask") {
    doLast{
        println("The task from plugin is available in the build script")
    }
}
