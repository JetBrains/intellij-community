def foo = {cl1, cl2 ->
  cl1.call()
  <caret>cl2.call()
}

foo {x->x} {y->y}