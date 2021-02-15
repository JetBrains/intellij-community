dependencies {
  compile(project(":javalib1"))
  test(project(":test1"))
  test(project(":test2"))
  implementation({ project(path: ":lib1"), project(path: ":lib2") }) // Not supported and thus skipped.
  androidTestImplementation(project(":test3"))
  androidTestImplementation(project(":test4"))
}
