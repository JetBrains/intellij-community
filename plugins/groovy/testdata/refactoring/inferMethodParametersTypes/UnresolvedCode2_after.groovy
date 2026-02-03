void foo(Object a) {

}

def <T extends Serializable & Unresolved> void m() {
  foo(null as T)
}
