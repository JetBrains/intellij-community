class C {
  Date field
}

enum E {
  val({C c ->
    c.wit<caret>h {
    }
  })
}