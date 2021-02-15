configurations {
  create("goodConfig")

  getByName("compile").isTransitive = true
  create("newConfig").isVisible = false
}
