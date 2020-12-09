android {
  dexOptions {
    additionalParameters = listOf("abcd")
    javaMaxHeapSize = "2048m"
    jumboMode = true
    keepRuntimeAnnotatedClasses = false
    maxProcessCount = 10
    optimize = true
    preDexLibraries = false
    threadCount = 5
  }
}
