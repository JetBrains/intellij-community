import org.jetbrains.annotations.NotNull

class GrTestFieldSingleConstructor {

  final field

  GrTestFieldSingleConstructor() { field = new Object() }

  def someMethod() { if (<warning descr="Condition 'field == null' is always false">field == null</warning>) {} }
}

class TestFieldTwoConstructors {

  final field

  // not null
  TestFieldTwoConstructors() { field = new Object() }

  // unknown
  TestFieldTwoConstructors(a) { field = a }

  def someMethod() { if (field == null) {} }
}

class TestFieldTwoNotNullConstructors {

  final field

  // not null
  TestFieldTwoNotNullConstructors() { field = new Object(); }

  // not null
  TestFieldTwoNotNullConstructors(@NotNull f) { field = f }

  def someMethod() { if (<warning descr="Condition 'field == null' is always false">field == null</warning>) {} }
}

class TestFieldDelegateConstructor {

  final field

  TestFieldDelegateConstructor() {
    this([])
  }

  TestFieldDelegateConstructor(a) {
    if (a == null) {
      field = new Object()
    }
    else {
      field = a
    }
  }

  def someMethod() { if (<warning descr="Condition 'field == null' is always false">field == null</warning>) {} }
}

class TestDelegateNull {

  final field

  TestDelegateNull(a, b) {
    this(a)
  }

  TestDelegateNull(@NotNull c) {
    field = null
  }

  def someMethod() { if (field == null) {} }
}
