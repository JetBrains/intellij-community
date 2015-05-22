import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class Next {
  def next() {}
}

def prefixNextNullable(@Nullable Next a) {
  <warning descr="Method invocation '++a' may produce 'java.lang.NullPointerException'">++a</warning>
}

def postfixNextNullable(@Nullable Next a) {
  <warning descr="Method invocation 'a++' may produce 'java.lang.NullPointerException'">a++</warning>
}

def prefixNextUnknown(Next a) {
  ++a
}

def postfixNextUnknown(Next a) {
  a++
}

def prefixNextNotNull(@NotNull Next a) {
  ++a
}

def postfixNextNotNull(@NotNull Next a) {
  a++
}


class UnknownNextCategory {
  static next(a) {}
}

class NullableNextCategory {
  static next(@Nullable a) {}
}

class NotNullNextCategory {
  static next(@NotNull a) {}
}

def categoryNextUnknown(Object a) {
  use(UnknownNextCategory) {
    a++
  }
  use(NullableNextCategory) {
    a++
  }
  use(NotNullNextCategory) {
    a++
  }
}

def categoryNextNullable(@Nullable Object a) {
  use(UnknownNextCategory) {
    <warning descr="Argument 'a' might be null but passed to non annotated parameter">a</warning>++
  }
  use(NullableNextCategory) {
    a++
  }
  use(NotNullNextCategory) {
    <warning descr="Argument 'a' might be null">a</warning>++
  }
}

def categoryNextNotNull(@NotNull Object a) {
  use(UnknownNextCategory) {
    a++
  }
  use(NullableNextCategory) {
    a++
  }
  use(NotNullNextCategory) {
    a++
  }
}

class NullableNext {
  @Nullable next() {}
}

def nonStandard(@NotNull NullableNext a) {
  def b = <warning descr="Expression 'a++' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a++</warning>
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {}
  <warning descr="Method invocation 'a.method()' may produce 'java.lang.NullPointerException'">a.method()</warning>
}
