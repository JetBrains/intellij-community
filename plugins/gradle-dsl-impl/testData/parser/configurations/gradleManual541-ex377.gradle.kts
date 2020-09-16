configurations {
  // declare a configuration that is going to resolve the compile classpath of the application
  compileClasspath.extendsFrom(someConfiguration)
  // declare a configuration that is going to resolve the runtime classpath of the application
  runtimeClasspath.extendsFrom(someConfiguration)
}
