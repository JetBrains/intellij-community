package com.siyeh.igtest.classlayout.final_method_in_final_class;

public final class FinalMethodInFinalClass
{
    public final void foo()
    {

    }
}
enum Fat {
    TRIGLYCERIDES {
        @Override
        public void eatProtein() {
        }
    }, CHOLESTEROL {
        @Override
        public void eatProtein() {
        }
    };

    public abstract void eatProtein();

    public final void eatCarbohydrates() {}

}
final class Soup {
  @java.lang.SafeVarargs
  private final void foo(java.util.Collection<String>... args) {
    // ...
  }

  @java.lang.SafeVarargs
  private static final void bar(java.util.Collection<Integer>... args) {}
}
