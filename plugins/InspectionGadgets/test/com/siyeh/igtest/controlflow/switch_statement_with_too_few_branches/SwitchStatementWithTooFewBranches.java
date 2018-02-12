package com.siyeh.igtest.controlflow.switch_statement_with_too_few_branches;

class SwitchStatementWithTooFewBranches {

  void foo(int i) {
    switch (i) {}
    <warning descr="'switch' has too few branches (1), and should probably be replaced with an 'if' statement">switch</warning> (i) {
      case 1:
        System.out.println(i);
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
