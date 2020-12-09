android {
  aaptOptions {
    additionalParameters = listOf("abcd")
    cruncherEnabled = true
    cruncherProcesses = 1
    failOnMissingConfigEntry = false
    ignoreAssetsPattern = "efgh"
    noCompress("a")
  }
}
