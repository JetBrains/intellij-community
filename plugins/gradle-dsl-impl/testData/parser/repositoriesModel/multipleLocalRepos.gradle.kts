allprojects {
 repositories {

  maven {
    name = "test2"
    url = uri("file:/some/other/repo")
  }
  maven {
    url = uri("file:/the/best/repo")
  }
  maven {
    url = uri("file:/some/repo")
  }
 }
}
