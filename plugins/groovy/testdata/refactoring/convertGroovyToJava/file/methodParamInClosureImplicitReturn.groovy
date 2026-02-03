def foo(int x) {
  [1, 2, 3] .each {
    print x
    x+=1
  }

  [1, 2, 3] .each {
    print x
    x++
  }

  print x
}