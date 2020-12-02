android {
  buildTypes {
    create("one") { }
    `create`("two") { }
    create("""three""") { }
    `create`("""four""") { }
    create("f\u0069ve") { }
    `create`("\u0073i\u0078") { }
  }
}
