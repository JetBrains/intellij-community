package javaOnly;

import test.*;
import static test.KmmLib_commonMain_apiKt.*;
import static test.KmmLib_jvmMain_apiKt.*;

public class JavaOnly_Main_Usages {
    public static void use(CommonMainExpect e) {
        // Refinement on libs work + checking that jvmMain and commonMain symbols are visible
        consumeJvmMainExpect(produceCommonMainExpect());
        consumeCommonMainExpect(produceJvmMainExpect());

        // Refinement on expects works
        produceCommonMainExpect().jvmApi();
    }
}
