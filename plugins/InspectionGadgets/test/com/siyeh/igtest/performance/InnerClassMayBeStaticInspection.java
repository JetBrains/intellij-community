package com.siyeh.igtest.performance;


public class InnerClassMayBeStaticInspection {
     class Nested {      
         public void foo() {
             bar("InnerClassMayBeStaticInspection.this");
         }

         private void bar(String string) {
         }
     }
}
