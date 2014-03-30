package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum Status { ACTIVE, INACTIVE, ERROR }

  private void foo (Status status) {
    switch (status)<caret> {
      case ACTIVE:
        break;
      case INACTIVE:
        break;
      default:
        throw new IllegalArgumentException("Unknown Status " + status);
    }
  }
}