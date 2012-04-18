def mkString(def i) {}

print([1, 2, 3].collect {
    mkString(it)
})


print([1, 2, 3].collect {
  it+1
})

Closure c1 = { mkString(it)}
Closure<Integer> c2 = {mkString(it); if (a) return 2<warning descr="Not all execution paths return a value">}</warning>
def c3 = {mkString(it)}