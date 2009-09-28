package com.siyeh.igtest.bugs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Frame;
import java.lang.Object;
import java.util.ArrayList;
import java.util.List;

public class CastConflictsWithInstanceofInspection {
    
    public void foo() {
        Number x = bar();

        System.out.println((Double) x);

        if (x instanceof Float) {
            System.out.println((Double) x); //warn here
        }
    }

    private Number bar() {
        return null;
    }

    void method(PsiElement p) {
        if (p instanceof PsiReferenceExpression) {
            PsiStatement stmt = (PsiStatement) p; // warn here
            PsiReferenceExpression ref = (PsiReferenceExpression) p; // no warn
        } else {
            PsiStatement stmt = (PsiStatement) p; // no warn
        }
    }


    boolean foo(Object o) {
        if (o instanceof List) {
            return !(o instanceof ArrayList) || ((ArrayList)o).get(0) == "asdf"; // no warn
        } else if (o instanceof JButton) {
            if (o instanceof Component) {
                return ((JComponent)o).isBackgroundSet(); // no warn
            }
        } else if (o instanceof Component) {
            return o instanceof Frame ? ((Frame)o).isFocusableWindow() : false; // no warn
        }
        return false;
    }

    Object boom(Object feed, boolean includeDisabled) {
        if (feed instanceof JComponent)
        {
            if (!(feed instanceof JButton) ||
                    ((JButton)feed).isDefaultButton()) // no warn
            {
                return feed;
            }

        } else {
            String string = (String)feed; // no warn
        }
        return null;
    }

    String baroom(Object o) {
        if (o != null && !(o instanceof Integer)) {
            return (String)o; // no warn
        }
        return null;
    }

    public void test(Object arg) {
        if (arg instanceof Byte ||
                arg instanceof Short && ((Short)arg).shortValue() == 0) { 
            // no warn
            // ...
        }
    }
}