repositories {
  maven {
    // Look for POMs and artifacts, such as JARs, here
    url = uri("http://repo2.mycompany.com/maven2")
    // Look for artifacts here if not found at the above location
    artifactUrls("http://repo.mycompany.com/jars")
    artifactUrls("http://repo.mycompany.com/jars2")
  }
}
