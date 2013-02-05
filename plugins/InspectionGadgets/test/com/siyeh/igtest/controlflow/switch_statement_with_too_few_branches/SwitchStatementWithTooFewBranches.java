package com.siyeh.igtest.controlflow.switch_statement_with_too_few_branches;

class SwitchStatementWithTooFewBranches {

  void foo(int i) {
    switch (i) {}
    switch (i) {
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
    switch (i)
  }
}
