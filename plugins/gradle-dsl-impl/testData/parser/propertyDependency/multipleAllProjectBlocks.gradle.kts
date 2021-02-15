allprojects { project ->
  apply(from="versions.gradle.kts")
}
subprojects { project ->
  apply(from="versions2.gradle.kts")
}
