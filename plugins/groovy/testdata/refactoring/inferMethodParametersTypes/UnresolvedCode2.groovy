def foo(a) {

}

def <T extends Serializable & Unresolved> void m() {
  foo(null as T)
}
