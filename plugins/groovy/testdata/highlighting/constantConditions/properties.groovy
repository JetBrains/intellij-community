import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable


class ClassWithUnknownField {
  def field
}

def getter(ClassWithUnknownField a) {
  def b = a.field
  b.method()
}

def setter(ClassWithUnknownField a) {
  a.field = <warning descr="Passing 'null' argument to non annotated parameter">null</warning>
}


class ClassWithNullableField {
  @Nullable field
}

def getter(ClassWithNullableField a) {
  def b = a.field
  <warning descr="Method invocation 'b.method()' may produce 'java.lang.NullPointerException'">b.method()</warning>
}

def setter(ClassWithNullableField a) {
  a.field = null
}


class ClassWithNotNullField {
  @NotNull field
}

def getter(ClassWithNotNullField a) {
  def b = a.field
  <warning descr="Qualifier 'b' is always not null">b</warning>?.method()
}

def setter(ClassWithNotNullField a) {
  a.field = <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>
}
