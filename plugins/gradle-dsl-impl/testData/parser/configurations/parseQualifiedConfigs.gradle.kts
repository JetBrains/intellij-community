configurations.create("badConfig")
configurations.create("otherBadConfig").isVisible = false
configurations {
  create("superBadConfig").isVisible = false
}
configurations.create("evenWorseConfig").isTransitive = true
