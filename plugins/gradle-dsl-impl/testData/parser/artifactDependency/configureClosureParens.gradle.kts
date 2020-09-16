dependencies {
  compile("org.hibernate:hibernate:3.1") {
    //in case of versions conflict "3.1" version of hibernate wins:
    force = true

    //excluding a particular transitive dependency:
    exclude(mapOf("module" to "cglib")) //by artifact name
    exclude(mapOf("group" to "org.jmock")) //by group
    exclude(mapOf("group" to "org.unwanted", "module" to "iAmBuggy")) //by both name and group

    //disabling all transitive dependencies of this dependency
    transitive = false
  }
}
