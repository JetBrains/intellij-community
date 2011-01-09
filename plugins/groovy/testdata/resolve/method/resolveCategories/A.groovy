class Cat {
  static def <T> T inject_(Object o, T initial, Closure<T> cl) {return inject_(o.iterator(), initial, cl)}

  static def <T> T inject_(Iterator o, T initial, Closure<T> cl) {
    return initial
  }
}

use(Cat) {
  [1, 2, 3].injec<ref>t_(2, {initial, item -> initial+item})
}