package com.siyeh.igtest.bugs.infinite_recursion;

import com.intellij.psi.PsiClass;

import java.util.List;
import java.io.IOException;
import java.io.File;

public class InfiniteRecursion
{
    public void foo()
    {
        new InfiniteRecursion().foo();
    }

    public void bar()
    {
        foo();
    }

    public int baz()
    {
        return baz();
    }

    public int bazoom()
    {
        if(foobar())
        {
            return bazoom();
        }
        return 3;
    }

    public void bazoomvoid()
    {
        if(foobar())
        {
            bazoomvoid();
        }
    }

    public int barangus()
    {
        while(foobar())
        {
            return barangus();
        }
        return 3;
    }

    public int barangoo()
    {
        do
        {
            return barangoo();
        }
        while(foobar());
    }

    public int bazoomer()
    {
        if(foobar())
        {
            return bazoomer();
        }
        else
        {
            return bazoomer() + 3;
        }
    }

    public boolean foobar()
    {
        return false && foobar();
    }

    public boolean foobarangus()
    {
        return foobarangus() && false;
    }

    public int bangem(PsiClass aClass)
    {
        final PsiClass superClass = aClass.getSuperClass();
        if(superClass ==null)
        {
            return 0;
        }
        else
        {
            return bangem(aClass)+1;
        }
    }

    private boolean foo(final PsiClass superClass)
    {
        return superClass ==null;
    }

    public int getInheritanceDepth(PsiClass aClass)
    {
        final PsiClass superClass = aClass.getSuperClass();
        if(superClass == null)
        {
            return 0;
        }
        else
        {
            return getInheritanceDepth(superClass) + 1;
        }
    }

     void rec(List pageConfig) {
        try {
            new File("c:/").getCanonicalFile();
        } catch (IOException e) {

        }
        for (int j = 0; j < pageConfig.size(); j++) {
            List pc = (List) pageConfig.get(j);
            rec(pc);
        }
    }

    void foo1() {
        for (;true && true || false;) {
            foo1();
        }
    }

    void foo2() {
        if (true || false) {
            foo2();
        }
    }

    void bar1() {
        while (true || false) {
            bar1();
        }
    }
}
