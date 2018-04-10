import java.util.*;

class LoopConditionNotUpdatedInsideLoop {
  void loopDoesNotLoop(boolean b) {
    while (b) {
      System.out.println("B");
      break;
    }
  }

  void doWhileFalse() {
    do {
      System.out.println();
    }
    while (false);
  }

  void polyadicExpression(boolean b1,
                          boolean b2,
                          boolean b3) {
    while (<warning descr="Variable 'b1' is not updated inside loop">b1</warning> &&
           <warning descr="Variable 'b2' is not updated inside loop">b2</warning> &&
           <warning descr="Variable 'b3' is not updated inside loop">b3</warning>) {
    }
  }

  void iterate(java.util.Iterator iterator) {
    while (<warning descr="Variable 'iterator' is not updated inside loop">iterator</warning>.hasNext()) {
    }
  }

  void finalLocal(java.io.InputStream in) throws java.io.IOException {
    final int i = in.read();
    while (<warning descr="Condition 'i != -1' is not updated inside loop">i != -1</warning>) {
    }
  }

  void nullCheck(Object o) {
    while (<warning descr="Variable 'o' is not updated inside loop">o</warning> != null) {
    }
  }

  void arrayUpdate1() {
    int[] sockets = new int[1];
    while (sockets[0] == 0) sockets = new int[1];
  }

  void arrayUpdate2() {
    int[] sockets = new int[1];
    while (sockets[0] == 0) sockets[0] = 1;
  }

  void arrayUpdate2Final() {
    final int[] sockets = new int[1];
    while (sockets[0] == 0) sockets[0] = 1;
  }

  void arrayUpdate3() {
    final int[] sockets = new int[1];
    int x = 0;
    while (<warning descr="Condition 'sockets[0] == 0' is not updated inside loop">sockets[0] == 0</warning>) x = 1;
  }

  public static <T> T getElementOfType(java.util.Collection<?> c, Class<T> clazz) {
    Object e = c.iterator().next();
    while (<warning descr="Variable 'e' is not updated inside loop">e</warning> != null) {
      if (clazz.isInstance(e)) {
        return (T)e;
      }
    }
    return null;
  }

  void forOk(int width, int height) {
    for(int i=0; i<width; i++) {
      for(int j=0; j<height; j++) {
        consume(i, j);
      }
    }
  }

  void forWrongCondition(int width, int height) {
    for(int i=0; i<width; i++) {
      for(int j=0; <warning descr="Variable 'i' is not updated inside loop">i</warning><<warning descr="Variable 'height' is not updated inside loop">height</warning>; j++) {
        consume(i, j);
      }
    }
  }

  void forWrongUpdate(int width, int height) {
    for(int i=0; i<width; i++) {
      for(int j=0; <warning descr="Variable 'j' is not updated inside loop">j</warning><<warning descr="Variable 'height' is not updated inside loop">height</warning>; i++) {
        consume(i, j);
      }
    }
  }

  native void consume(int i, int j);

  final static class Node {
    private final Node parent;
    private final Object type;

    Node(Node parent, Object type) {
      this.parent = parent;
      this.type = type;
    }

    Node getParent() { return parent; }

    Object getType() { return type; }
  }

  native Node getNode();

  String testWhile() {
    Node node = getNode();
    Node parent = node.getParent();
    while(<warning descr="Variable 'parent' is not updated inside loop">parent</warning> != null) {
      if (parent.getType() instanceof String) {
        return parent.toString();
      }
      //parent = parent.getParent();
    }
    return "";
  }

  String testWhileOk() {
    Node node = getNode();
    Node parent = node.getParent();
    while(parent != null) {
      if (parent.getType() instanceof String) {
        return parent.toString();
      }
      parent = parent.getParent();
    }
    return "";
  }

  // IDEA-166869
  void test(List<String> lines) {
    int i = 0;
    for (int size = lines.size(); i < size; i++) {
      if (lines.get(i).isEmpty()) break;
    }
    System.out.println(i);
  }

  void test2(List<String> lines) {
    int i = 0, j = 0;
    for (int size = lines.size(); <warning descr="Variable 'i' is not updated inside loop">i</warning> < <warning descr="Variable 'size' is not updated inside loop">size</warning>; j++) {
      if (lines.get(i).isEmpty()) break;
    }
    System.out.println(j);
  }

  class Holder {
    volatile boolean x;

    final boolean isX() {
      return x;
    }
  }

  Runnable testVolatile(final Holder h) {
    Runnable r = () -> {
      while (!h.isX());
      System.out.println("Has!");
    };
    return r;
  }
}