def foo(a) {

}

def <T extends Serializable & Comparable> void m() {
  foo(null as List<T>)
}