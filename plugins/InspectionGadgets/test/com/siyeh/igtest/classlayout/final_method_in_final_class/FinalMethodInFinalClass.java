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
