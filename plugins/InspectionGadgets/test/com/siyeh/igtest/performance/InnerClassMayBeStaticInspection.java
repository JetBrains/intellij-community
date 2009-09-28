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
class IDEADEV_5513 {

    private static class Inner  {
        
        private boolean b = false;

        private class InnerInner {

            public void foo() {
                b = true;
            }
        }
    }
}