repositories {
  jcenter()
  maven {
    url = uri("good.url")
    name = "Good Name"
    artifactUrls("nice.url", "other.nice.url")
  }
}
