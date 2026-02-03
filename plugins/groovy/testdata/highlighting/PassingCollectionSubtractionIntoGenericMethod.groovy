def foo(Set<String> zzz) {
  def xxx = ['a'] as Set<String>
  while (xxx) {
    def sub = xxx - xxx.iterator().next()
    xxx = bar(sub, zzz)
    <warning descr="Cannot resolve symbol 'doo'">doo</warning>()
  }
}

def <T> Set<T> bar(Set<T> xxx, Set<T> yyy) { xxx }
