void foo(List<Object> a) {

}

def <T extends Serializable & Comparable> void m() {
  foo(null as List<T>)
}