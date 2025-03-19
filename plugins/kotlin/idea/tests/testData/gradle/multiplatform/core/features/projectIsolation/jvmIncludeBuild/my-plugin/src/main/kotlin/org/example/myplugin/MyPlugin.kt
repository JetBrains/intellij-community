package org.example.myplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class MyPlugin : Plugin<Project> {
    override fun <!LINE_MARKER("descr='Implements function in Plugin (org.gradle.api) Press ... to navigate'")!>apply<!>(project: Project) {
        project.tasks.register<org.gradle.api.DefaultTask>("helloFromPlugin") {
            group = "MyPluginTasks"
            description = "Example task from custom plugin"
            doLast {
                println("Hello from MyPlugin!")
            }
        }
    }
}

