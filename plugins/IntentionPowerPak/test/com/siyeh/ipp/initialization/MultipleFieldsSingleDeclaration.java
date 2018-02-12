class MultipleFieldsSingleDeclaration {

  String <caret>s = "", array[] = {s};

  {
    System.out.println();
  }
}