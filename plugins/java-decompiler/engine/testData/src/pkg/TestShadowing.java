package pkg;

import java.util.Calendar;

class TestShadowing extends TestShadowingSuperClass {
  ext.Shadow.B instanceOfB = new ext.Shadow.B();
  Calendar.Builder calBuilder = new Calendar.Builder();
}

class TestShadowingSuperClass {
  static class Builder { }
}