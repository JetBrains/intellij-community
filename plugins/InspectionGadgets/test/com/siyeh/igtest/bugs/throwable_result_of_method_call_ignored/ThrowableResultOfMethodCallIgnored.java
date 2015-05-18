package com.siyeh.igtest.bugs.throwable_result_of_method_call_ignored;


public class ThrowableResultOfMethodCallIgnored {
    public static void test() {
        try {
            <warning descr="Result of 'firstNonNull()' not thrown">firstNonNull</warning>(new Throwable(), null);
        }
        catch (Exception e) {
            throw new RuntimeException(firstNonNull(e.getCause(), e));
        }
    }

    public static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    void m() {
      throw (RuntimeException) b();
    }

    public Exception b() {
      return new RuntimeException();
    }
}
class ResWrap {
    private String        payload;
    private Throwable    error;

    public Throwable getError() {
        return error;
    }
    public ResWrap service() {
        final ResWrap result = new ResWrap();
        if (result.getError() == null) {
            //rememberResult(result.payload);
        }
        return result;
    }
}

interface I {
   Exception get();
}

class LambdaReturn {
    {
        I i = () -> createException("foo"); 
    }

    private RuntimeException createException(String message) {
        return new RuntimeException(message);
    }
}