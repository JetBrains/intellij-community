void foo(Integer a) {

}

def <T extends Integer> void m(T t) {
  foo(t)
}
