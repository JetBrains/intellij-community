package com.siyeh.igtest.initialization.instance_variable_uninitialized_use;

import java.io.IOException;

class InstanceVariableUnitializedUse {
  int i;

  InstanceVariableUnitializedUse() throws IOException {
    try (java.io.FileInputStream in = new java.io.FileInputStream("asdf" + (i=3) + "asdf")) {}
    System.out.println(i);
  }
}

class InstanceFieldVsDoWhile {
  private Object object;

  public InstanceFieldVsDoWhile() {
    do {
      object = new Object();
    } while (object.hashCode() < 1000); // Instance field used before initialization
  }
}

class FinalField {
  private final Object object;

  FinalField() {
    System.out.println(<error descr="Variable 'object' might not have been initialized">object</error>);
    object = null;
  }
}
class SwitchExpression {
  private int i;

  SwitchExpression(E e, int z) {
    int x;
    x = switch (e) {
      case A:
        i = 1;
        break 2;
      case B:
        i = 2;
        break 3;
      case C:
        i = 3;
        break 4;
    };
    System.out.println(i);
  }

  enum E {
    A, B, C
  }

  SwitchExpression() {
    int z = switch (10) {
      case 1, 2 -> i = 9;
      default -> i = 10;
    };
    System.out.println(i);
  }
}
class Lambda {
  int i;
  Lambda() {
    Runnable r = () -> {
      i = 10;
    };
    System.out.println(<warning descr="Instance field 'i' used before initialized">i</warning>);
  }
}
