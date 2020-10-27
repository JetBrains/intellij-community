configurations {
  create("rejectConfig") {
    resolutionStrategy {
      componentSelection {
        // Accept the highest version matching the requested version that isn't '1.5'
        all {
          if (candidate.group == 'org.sample' && candidate.module == 'api' && candidate.version == '1.5') {
            reject("version 1.5 is broken for 'org.sample:api'")
          }
        }
      }
    }
  }
}

dependencies {
  "rejectConfig"("org.sample:api:1.+")
}
