import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

def notNullBooleanConditions(@NotNull Boolean a) {
  if (a) {
    if (<warning descr="Condition 'a' is always true">a</warning>) {}
    if (<warning descr="Condition '!a' is always false">!a</warning>) {}

    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    if (<warning descr="Condition 'a == true' is always true">a == true</warning>) {}
    if (<warning descr="Condition 'a != true' is always false">a != true</warning>) {}
    if (<warning descr="Condition 'a == false' is always false">a == false</warning>) {}
    if (<warning descr="Condition 'a != false' is always true">a != false</warning>) {}

    if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
    if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
    if (<warning descr="Condition '!a == true' is always false">!a == true</warning>) {}
    if (<warning descr="Condition '!a != true' is always true">!a != true</warning>) {}
    if (<warning descr="Condition '!a == false' is always true">!a == false</warning>) {}
    if (<warning descr="Condition '!a != false' is always false">!a != false</warning>) {}
  }
  else {
    if (<warning descr="Condition 'a' is always false">a</warning>) {}
    if (<warning descr="Condition '!a' is always true">!a</warning>) {}

    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    if (<warning descr="Condition 'a == true' is always false">a == true</warning>) {}
    if (<warning descr="Condition 'a != true' is always true">a != true</warning>) {}
    if (<warning descr="Condition 'a == false' is always true">a == false</warning>) {}
    if (<warning descr="Condition 'a != false' is always false">a != false</warning>) {}

    if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
    if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
    if (<warning descr="Condition '!a == true' is always true">!a == true</warning>) {}
    if (<warning descr="Condition '!a != true' is always false">!a != true</warning>) {}
    if (<warning descr="Condition '!a == false' is always false">!a == false</warning>) {}
    if (<warning descr="Condition '!a != false' is always true">!a != false</warning>) {}
  }
}

def unknownBooleanConditions(Boolean a) {
  if (a) {
    if (<warning descr="Condition 'a' is always true">a</warning>) {}
    if (<warning descr="Condition '!a' is always false">!a</warning>) {}

    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    if (<warning descr="Condition 'a == true' is always true">a == true</warning>) {}
    if (<warning descr="Condition 'a != true' is always false">a != true</warning>) {}
    if (<warning descr="Condition 'a == false' is always false">a == false</warning>) {}
    if (<warning descr="Condition 'a != false' is always true">a != false</warning>) {}

    if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
    if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
    if (<warning descr="Condition '!a == true' is always false">!a == true</warning>) {}
    if (<warning descr="Condition '!a != true' is always true">!a != true</warning>) {}
    if (<warning descr="Condition '!a == false' is always true">!a == false</warning>) {}
    if (<warning descr="Condition '!a != false' is always false">!a != false</warning>) {}
  }
  else {
    if (<warning descr="Condition 'a' is always false">a</warning>) {}
    if (<warning descr="Condition '!a' is always true">!a</warning>) {}

    if (a == null) {}
    if (a != null) {}
    if (<warning descr="Condition 'a == true' is always false">a == true</warning>) {}
    if (<warning descr="Condition 'a != true' is always true">a != true</warning>) {}
    if (a == false) {}
    if (a != false) {}

    if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
    if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
    if (<warning descr="Condition '!a == true' is always true">!a == true</warning>) {}
    if (<warning descr="Condition '!a != true' is always false">!a != true</warning>) {}
    if (<warning descr="Condition '!a == false' is always false">!a == false</warning>) {}
    if (<warning descr="Condition '!a != false' is always true">!a != false</warning>) {}
  }
}

def nullableBooleanConditions(@Nullable Boolean a) {
  if (a) {
    if (<warning descr="Condition 'a' is always true">a</warning>) {}
    if (<warning descr="Condition '!a' is always false">!a</warning>) {}

    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    if (<warning descr="Condition 'a == true' is always true">a == true</warning>) {}
    if (<warning descr="Condition 'a != true' is always false">a != true</warning>) {}
    if (<warning descr="Condition 'a == false' is always false">a == false</warning>) {}
    if (<warning descr="Condition 'a != false' is always true">a != false</warning>) {}

    if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
    if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
    if (<warning descr="Condition '!a == true' is always false">!a == true</warning>) {}
    if (<warning descr="Condition '!a != true' is always true">!a != true</warning>) {}
    if (<warning descr="Condition '!a == false' is always true">!a == false</warning>) {}
    if (<warning descr="Condition '!a != false' is always false">!a != false</warning>) {}
  }
  else {
    if (<warning descr="Condition 'a' is always false">a</warning>) {}
    if (<warning descr="Condition '!a' is always true">!a</warning>) {}

    if (a == null) {}
    if (a != null) {}
    if (<warning descr="Condition 'a == true' is always false">a == true</warning>) {}
    if (<warning descr="Condition 'a != true' is always true">a != true</warning>) {}
    if (a == false) {}
    if (a != false) {}

    if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
    if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
    if (<warning descr="Condition '!a == true' is always true">!a == true</warning>) {}
    if (<warning descr="Condition '!a != true' is always false">!a != true</warning>) {}
    if (<warning descr="Condition '!a == false' is always false">!a == false</warning>) {}
    if (<warning descr="Condition '!a != false' is always true">!a != false</warning>) {}
  }
}
