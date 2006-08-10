package com.siyeh.igtest.style;

import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class One extends ExpressionInspection {

    public BaseInspectionVisitor buildVisitor() {
        return null;
    }

    public String getGroupDisplayName() {
        return null;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    private static class Inner {
        private static boolean test() {
            return false;
        }

    }
}
class TestVariableScope {
    public static void main(String[] args) {
        int foo;
        // blah
        if (true) {
            foo = 5;
        }

        doStuff(foo);
    }

    private static void doStuff(int foo) {
    }

}