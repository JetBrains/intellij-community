def foo(String s) {
  return this
}

def bar(String s) {
  return 2
}

def foo = [{this}]

def var = foo[0] "a" bar "a"
print  va<ref>r