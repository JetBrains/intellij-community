configurations {
  create("compileClasspath") { // the example (without create()) does not sync
    resolutionStrategy.force("commons-codec:commons-codec:1.9")
  }
}

dependencies {
  implementation("org.apache.httpcomponents:httpclient:4.5.4")
}
