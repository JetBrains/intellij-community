import org.jetbrains.completion.full.line.gradle.Plugins

intellij {
    this.plugins.set(listOf(Plugins.python))
}
tasks.withType<org.jetbrains.intellij.tasks.PrepareSandboxTask> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

dependencies {
    implementation(project(":languages:common"))

    testImplementation(project(":languages:common").dependencyProject.sourceSets["test"].output)
}
