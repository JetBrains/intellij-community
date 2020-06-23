void foo(Iterable<String> xs) {
  for (a in xs) {
    bar(a)
  }
}

def bar(String s) {}