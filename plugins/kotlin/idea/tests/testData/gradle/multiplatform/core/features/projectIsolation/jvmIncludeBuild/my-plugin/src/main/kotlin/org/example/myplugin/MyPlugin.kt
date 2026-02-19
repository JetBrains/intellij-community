package org.example.myplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask

class MyPlugin : Plugin<Project> {
    override fun <!LINE_MARKER("descr='Implements function in Plugin (org.gradle.api) Press ... to navigate'")!>apply<!>(project: Project) {
        project.tasks.register("helloFromPlugin", DefaultTask::class.java) { task ->
            task.group = "MyPluginTasks"
            task.description = "Example task from custom plugin"
            task.doLast {
                println("Hello from MyPlugin!")
            }
        }
    }
}
