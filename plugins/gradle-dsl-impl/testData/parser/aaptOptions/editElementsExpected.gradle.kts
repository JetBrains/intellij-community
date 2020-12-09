android {
  aaptOptions {
    additionalParameters = listOf("abcd", "xyz")
    cruncherEnabled = true
    cruncherProcesses = 3
    failOnMissingConfigEntry = false
    ignoreAssetsPattern = "mnop"
    noCompress("a", "c")
  }
}
