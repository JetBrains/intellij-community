configurations {
  create("badConfig") {
    isTransitive = true
  }
  create("worseConfig")
  create("worstConfig").isVisible = false
}
