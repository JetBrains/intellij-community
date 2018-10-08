package com.siyeh.igtest.controlflow.switch_statement_with_too_few_branches;

class SwitchStatementWithTooFewBranches {

  void foo(int i) {
    switch (i) {}
    <warning descr="'switch' statement has too few case labels (1), and should probably be replaced with an 'if' statement">switch</warning> (i) {
      case 1:
        System.out.println(i);
    }
    <warning descr="'switch' statement has too few case labels (1), and should probably be replaced with an 'if' statement">switch</warning>(i) {
      case 1:
        System.out.println(1);
    }
    <warning descr="'switch' statement has only 'default' case">switch</warning>(i) {
      default:
        System.out.println(2);
    }
    switch(i) {
      case 1:
        System.out.println(1);
      case 2:
        System.out.println(2);
    }
    switch(i) {
      case 1:
        System.out.println(1);
      case 2:
        System.out.println(2);
      default:
        System.out.println(3);
    }
    switch(i) {
      case 1:
        break;
      case 2:
        break;
      case 3:
        break;

    }
    switch (i)<EOLError descr="'{' expected"></EOLError>
  }
}
