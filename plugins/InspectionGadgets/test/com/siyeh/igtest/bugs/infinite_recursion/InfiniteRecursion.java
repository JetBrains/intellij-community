package com.siyeh.igtest.bugs.infinite_recursion;

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

    public int <warning descr="Method 'baz()' recurses infinitely, and can only end by throwing an exception">baz</warning>()
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

    public int <warning descr="Method 'barangoo()' recurses infinitely, and can only end by throwing an exception">barangoo</warning>()
    {
        do
        {
            return barangoo();
        }
        while(foobar());
    }

    public int <warning descr="Method 'bazoomer()' recurses infinitely, and can only end by throwing an exception">bazoomer</warning>()
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

    public boolean <warning descr="Method 'foobarangus()' recurses infinitely, and can only end by throwing an exception">foobarangus</warning>()
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

    void <warning descr="Method 'foo1()' recurses infinitely, and can only end by throwing an exception">foo1</warning>() {
        for (;true && true || false;) {
            foo1();
        }
    }

    void <warning descr="Method 'foo2()' recurses infinitely, and can only end by throwing an exception">foo2</warning>() {
        if (true || false) {
            foo2();
        }
    }

    void <warning descr="Method 'bar1()' recurses infinitely, and can only end by throwing an exception">bar1</warning>() {
        while (true || false) {
            bar1();
        }
    }
}

interface PsiClass {
    PsiClass getSuperClass();
}