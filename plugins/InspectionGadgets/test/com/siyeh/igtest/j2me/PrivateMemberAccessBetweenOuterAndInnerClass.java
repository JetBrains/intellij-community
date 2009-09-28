package com.siyeh.igtest.j2me;

import java.awt.*;


public class PrivateMemberAccessBetweenOuterAndInnerClass {
    private String caption = "Button";

    private void initialize() {
        Button btn = new Button(caption) {
            public void foo() {
                System.out.println(caption);
            }
        };
    }
}
