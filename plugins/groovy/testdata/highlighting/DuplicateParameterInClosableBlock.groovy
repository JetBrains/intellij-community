def foo = 2;

print {def <error descr="Variable 'foo' already defined">foo</error>, def <error descr="Variable 'foo' already defined">foo</error> ->
  print foo
}

