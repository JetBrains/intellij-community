import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable


class ClassWithUnknownGetPut {
  def getAt(a) {}

  def putAt(a, b) {}
}

def getPut(ClassWithUnknownGetPut a) {
  def b = a[1]
  b.method()
  def c = a[<warning descr="Passing 'null' argument to non annotated parameter">null</warning>]
  c.method()
  a[1] = ""
  a[2] = <warning descr="Passing 'null' argument to non annotated parameter">null</warning>
}


class ClassWithNullableGetPut {
  @Nullable getAt(@Nullable a) {}

  def putAt(@Nullable a, @Nullable b) {}
}

def getPut(ClassWithNullableGetPut a) {
  def b = a[1]
  <warning descr="Method invocation 'b.method()' may produce 'java.lang.NullPointerException'">b.method()</warning>
  def c = a[null]
  <warning descr="Method invocation 'c.method()' may produce 'java.lang.NullPointerException'">c.method()</warning>
  a[1] = ""
  a[2] = null
}


class ClassWithNotNullGetPut {
  @NotNull getAt(@NotNull a) {}

  def putAt(@NotNull a, @NotNull b) {}
}

def getPut(ClassWithNotNullGetPut a) {
  def b = a[1]
  b.method()
  def c = a[<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>]
  c.method()
  a[1] = ""
  a[2] = <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>
  a[<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>] = ""
  a[<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>] = <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>
}
