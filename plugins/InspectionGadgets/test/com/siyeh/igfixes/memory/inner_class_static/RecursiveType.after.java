class Usage {

  {
    new Node(0, new Node(1, null));
  }

  private static class N<caret>ode {
    Node(int idx, Node next) {
    }
  }
}
