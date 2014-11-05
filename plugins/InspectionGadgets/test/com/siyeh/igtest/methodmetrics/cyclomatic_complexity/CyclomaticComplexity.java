package com.siyeh.igtest.methodmetrics.cyclomatic_complexity;

public class CyclomaticComplexity
{
    public void <warning descr="Overly complex method 'fooBar()' (cyclomatic complexity = 12)">fooBar</warning>()
    {
        int i = 0;
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        if(bar())
        {
            i++;
        }
        System.out.println("i = " + i);
    }

    private boolean bar()
    {
        return true;
    }

    private boolean <warning descr="Overly complex method 'polyadic()' (cyclomatic complexity = 12)">polyadic</warning>(boolean a, boolean b, boolean c) {
        return a && b || c && a || b && c || a && b || c && a || b && c;
    }

    private void <warning descr="Overly complex method 'tryCatch()' (cyclomatic complexity = 15)">tryCatch</warning>() {
        try {
        } catch (ArithmeticException e) {
        } catch (ArrayStoreException e) {
        } catch (ClassCastException e) {
        } catch (IllegalArgumentException e) {
        } catch (NegativeArraySizeException e) {
        } catch (NullPointerException e) {
        } catch (UnsupportedOperationException e) {
        }
        try {
        } catch (ArithmeticException e) {
        } catch (ArrayStoreException e) {
        } catch (ClassCastException e) {
        } catch (IllegalArgumentException e) {
        } catch (NegativeArraySizeException e) {
        } catch (NullPointerException e) {
        } catch (UnsupportedOperationException e) {
        }
    }
}
