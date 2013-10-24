package com.siyeh.igtest.controlflow.conditional_expression_with_identical_branches;

class ConditionalExpressionWithIdenticalBranches {

  int one(boolean b) {
    return b ? 1 + 2 + 3 : 1 + 2 + 3;
  }

  int two(boolean b) {
    return b ? 1 + 2 : 1 + 2 + 3;
  }

  Class<String> three(boolean b) {
    return b ? java.lang.String.class : String.class;
  }

  int incomplete(boolean b) {
    return b?
  }
}