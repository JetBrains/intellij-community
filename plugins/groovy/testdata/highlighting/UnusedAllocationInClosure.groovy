def cl = {
  new <warning descr="Result of 'new Object()' is ignored">Object</warning>()
  new Object()
}
print cl()