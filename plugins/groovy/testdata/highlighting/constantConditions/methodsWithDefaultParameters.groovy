import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

def methodWithUnknownParameter1(a = null) {
  if (a == null) {}
  if (a != null) {}
}

def methodWithUnknownParameter2(a = null) {
  <warning descr="Method invocation 'a.method()' may produce 'java.lang.NullPointerException'">a.method()</warning>
}

def methodWithUnknownParameter3(a = "") {
  if (a == null) {}
  if (a != null) {}
}

def methodWithUnknownParameter4(a = "") {
  a.method()
}


def methodWithNullableParameter1(@Nullable a = null) {
  if (a == null) {}
  if (a != null) {}
}

def methodWithNullableParameter2(@Nullable a = null) {
  <warning descr="Method invocation 'a.method()' may produce 'java.lang.NullPointerException'">a.method()</warning>
}

def methodWithNullableParameter3(@Nullable a = "") {
  if (a == null) {}
  if (a != null) {}
}

def methodWithNullableParameter4(@Nullable a = "") {
  <warning descr="Method invocation 'a.method()' may produce 'java.lang.NullPointerException'">a.method()</warning>
}



def methodWithNotNullParameter(@NotNull a = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>) {
  if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
  if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
  <warning descr="Qualifier 'a' is always not null">a</warning>?.method()
}

