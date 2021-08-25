// "Create missing switch branch 'ERROR'" "true"
package com.siyeh.ipp.enumswitch;

class Main {
  enum Status { ACTIVE, INACTIVE, ERROR }

  private void foo (Status status) {
    switch (status)<caret> {
      case ACTIVE:
        break;
      case INACTIVE:
        break;
      case default:
        throw new IllegalArgumentException("Unknown Status " + status);
    }
  }
}