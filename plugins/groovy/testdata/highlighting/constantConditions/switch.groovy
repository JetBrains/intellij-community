import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class SomeClass {}

def switchUnknown(a) {
  switch (a) {
    case 0:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always false">a</warning>) {}
      if (<warning descr="Condition '!a' is always true">!a</warning>) {}
      break;
    case 1:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always true">a</warning>) {}
      if (<warning descr="Condition '!a' is always false">!a</warning>) {}
      break;
    case SomeClass:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
      break;
    case []:
      if (a == null) {}
      if (a != null) {}
      if (a) {}
      if (!a) {}
      break;
    case b:
      if (a == null) {}
      if (a != null) {}
      if (a) {}
      if (!a) {}
      break;
    case null:
      if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always false">a</warning>) {}
      if (<warning descr="Condition '!a' is always true">!a</warning>) {}
      break;
    default:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
  }
}


def testSwitchNullable(@Nullable a) {
  switch (<warning descr="Argument 'a' might be null but passed to non annotated parameter">a</warning>) {
    case 0:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always false">a</warning>) {}
      if (<warning descr="Condition '!a' is always true">!a</warning>) {}
      break;
    case 1:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always true">a</warning>) {}
      if (<warning descr="Condition '!a' is always false">!a</warning>) {}
      break;
    case SomeClass:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
      break;
    case []:
      if (<warning descr="Condition 'a instanceof SomeClass' is always false">a instanceof SomeClass</warning>) {}
      if (a == null) {}
      if (a != null) {}
      if (a) {}
      if (!a) {}
      break;
    case b:
      if (a == null) {}
      if (a != null) {}
      if (a) {}
      if (!a) {}
      break;
    case null:
      if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always false">a</warning>) {}
      if (<warning descr="Condition '!a' is always true">!a</warning>) {}
      break;
    default:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
  }
}


def testSwitchNotNull(@NotNull a) {
  switch (a) {
    case 0:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always false">a</warning>) {}
      if (<warning descr="Condition '!a' is always true">!a</warning>) {}
      break;
    case 1:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (<warning descr="Condition 'a' is always true">a</warning>) {}
      if (<warning descr="Condition '!a' is always false">!a</warning>) {}
      break;
    case SomeClass:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
      break;
    case []:
      if (<warning descr="Condition 'a instanceof SomeClass' is always false">a instanceof SomeClass</warning>) {}
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
      break;
    case b:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
      break;
    case <warning descr="Condition 'null' is always false">null</warning>:
      if (a == null) {}
      if (a != null) {}
      if (a) {}
      if (!a) {}
      break;
    default:
      if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
      if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
      if (a) {}
      if (!a) {}
  }
}
