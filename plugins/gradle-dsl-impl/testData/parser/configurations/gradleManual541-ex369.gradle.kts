configurations {
  create("pluginTool") {
    defaultDependencies {
      add(project.dependencies.create("org.gradle:myutil:1.0"))
    }
  }
}
