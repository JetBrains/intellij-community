package com.siyeh.igtest.style.unqualified_field_access;

public class UnqualifiedFieldAccess {

        private String field;

        public void x () {
                field = "foofoo";
                final String s = String.valueOf(field.hashCode());
                System.out.println(s);
        }

  void foo() {
    new Object() {
      int i;
      void foo() {
        new Object() {
          void foo() {
            i  = 0;
          }
        };
      }
    };
  }

  void simpleAnonymous() {
    new Object() {
      String s;

      void foo() {
        System.out.println(s);
      }
    };
  }
}