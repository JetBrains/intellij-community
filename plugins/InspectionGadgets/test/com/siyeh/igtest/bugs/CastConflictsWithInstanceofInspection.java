package com.siyeh.igtest.bugs;

import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiElement;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.lang.*;
import java.lang.Object;
import java.util.List;
import java.util.ArrayList;
import java.util.AbstractList;
import java.awt.Component;
import java.awt.Frame;

public class CastConflictsWithInstanceofInspection {
    public void foo() {
        Number x = bar();

        System.out.println((Double) x);

        if (x instanceof Float) {
            System.out.println((Double) x);
        }
    }

    private Number bar() {
        return null;
    }

    void method(PsiElement p) {
        if (p instanceof PsiReferenceExpression) {
            PsiStatement stmt = (PsiStatement) p;
            PsiReferenceExpression ref = (PsiReferenceExpression) p;
        } else {
            PsiStatement stmt = (PsiStatement) p;

        }

    }


    boolean foo(Object o) {
        if (o instanceof List) {
            return !(o instanceof ArrayList) || ((ArrayList)o).get(0) == "asdf";
        } else if (o instanceof JButton) {
            if (o instanceof Component) {
                return ((JComponent)o).isBackgroundSet();
            }
        } else if (o instanceof Component) {
            return o instanceof Frame ? ((Frame)o).isFocusableWindow() : false;
        }
        return false;
    }

}
