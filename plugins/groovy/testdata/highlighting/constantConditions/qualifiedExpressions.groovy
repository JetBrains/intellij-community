import org.jetbrains.annotations.Contract

class Test {
  def field

  @Contract(pure = true)
  def pure() {}

  def nonPure() {}
}


def getter(Test a) {
  if (a.field == null) {
    if (<warning descr="Condition 'a.field' is always false">a.field</warning>) {}

    def b = a.field
    if (<warning descr="Condition 'b' is always false">b</warning>) {}
    if (<warning descr="Condition 'b == null' is always true">b == null</warning>) {}
    if (<warning descr="Condition '!b' is always true">!b</warning>) {}
  }
}

def pureMethodAfterNonPure(Test a) {
  if (a.pure()) {
    if (<warning descr="Condition 'a.pure()' is always true">a.pure()</warning>) {}
    a.nonPure()
    if (a.pure()) {}
  }
}

def testGetterThenVariable(Test a) {
  def c = a.field
  if (a.field) {
    if (<warning descr="Condition 'c' is always true">c</warning>) {}
    a.nonPure()
    if (<warning descr="Condition 'c' is always true">c</warning>) {}
    if (a.field) {}
  }
}

def testVariableThenGetter(Test a) {
  def c = a.field
  if (c) {
    if (<warning descr="Condition 'a.field' is always true">a.field</warning>) {}
    a.nonPure()
    if (<warning descr="Condition 'c' is always true">c</warning>) {}
    if (a.field) {}
  }
}
