repositories {
  jcenter()
  maven {
    url = uri("good.url")
    name = "Good Name"
    credentials {
      username = "joe.bloggs"
      password = "12345678"
    }
  }
}
