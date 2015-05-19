import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

def unknownConditions(a) {
  if (a) {}
  if (!a) {}

  if (a == true) {}
  if (a != true) {}
  if (a == false) {}
  if (a != false) {}
  if (a == null) {}
  if (a != null) {}
  if (a == 10) {}
  if (a != 10) {}

  if (!a == true) {}
  if (!a != true) {}
  if (!a == false) {}
  if (!a != false) {}
  if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
  if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
}

def notNullConditions(@NotNull a) {
  if (a) {}
  if (!a) {}

  if (a == true) {}
  if (a != true) {}
  if (a == false) {}
  if (a != false) {}
  if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
  if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
  if (a == 10) {}
  if (a != 10) {}

  if (!a == true) {}
  if (!a != true) {}
  if (!a == false) {}
  if (!a != false) {}
  if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
  if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
}

def nullableConditions(@Nullable a) {
  if (a) {}
  if (!a) {}

  if (a == true) {}
  if (a != true) {}
  if (a == false) {}
  if (a != false) {}
  if (a == null) {}
  if (a != null) {}
  if (a == 10) {}
  if (a != 10) {}

  if (!a == true) {}
  if (!a != true) {}
  if (!a == false) {}
  if (!a != false) {}
  if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
  if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
}
