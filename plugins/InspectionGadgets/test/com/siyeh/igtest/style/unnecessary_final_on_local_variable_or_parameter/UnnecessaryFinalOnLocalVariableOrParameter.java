package com.siyeh.igtest.style.unnecessary_final_on_local_variable_or_parameter;

import java.io.*;

public class UnnecessaryFinalOnLocalVariableOrParameter {
  class XX {
    XX(Object o) {}

    void foo(<warning descr="Unnecessary 'final' on parameter 'o'">final</warning> Object o) {
      new XX(o) {};
    }

    void m(final Object o) {
      new XX(null) {
        Object b = o;
      };
    }
    
    void fx(final Object o) {
      new XX(new XX(null) {
        Object b = o;
      });
    }

    void tryWithResources() throws IOException {
      try (<warning descr="Unnecessary 'final' on variable 'in'">final</warning> InputStream in = new FileInputStream("")){
        new Object() {{
          System.out.println(in);
        }};
      } catch (<warning descr="Unnecessary 'final' on parameter 'e'">final</warning> RuntimeException | AssertionError e) {
        class X {
          void m() {
            System.out.println(e);
          }
        }
      }
    }
  }
}