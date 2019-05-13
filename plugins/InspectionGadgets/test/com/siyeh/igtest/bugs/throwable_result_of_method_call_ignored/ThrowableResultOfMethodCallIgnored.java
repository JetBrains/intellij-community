package com.siyeh.igtest.bugs.throwable_result_of_method_call_ignored;

import java.util.*;

public class ThrowableResultOfMethodCallIgnored {

    Exception e = b();

    public static void test() {
        try {
            firstNonNull(new Throwable(), null);
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

    void n(int i) throws Exception {
        throw i == 0 ? null : b();
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
        if (result.getError() instanceof Error) {
            // todo
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
/**
 * An exception with fluent setters
 */
class FluentException extends Exception {
    private String info;

    private FluentException() {
    }

    private FluentException( String s ) {
        super( s );
    }

    public FluentException withInfo( String s ) {
        info = s;
        return this;
    }

    public static FluentException factory( String msg ) {
        return new FluentException( msg );
    }

    public String getInfo() {
        return info;
    }
}

/**
 * A sample call site
 */
class TestIt {
    public void test() throws FluentException {
        FluentException.<warning descr="Result of 'factory()' not thrown">factory</warning>( "foo" ).withInfo( "bar" );
        throw FluentException.factory( "foo" ).withInfo( "bar" );
    }
}
class Throwables {
    void m(Exception e) {
        String s = getRootCause(e).getMessage();
        System.out.println(s);

        <warning descr="Result of 'getRootCause()' not thrown">getRootCause</warning>(e);
    }

    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause;
        Throwable root = throwable;
        while ((cause = root.getCause()) != null) {
            root = cause;
        }
        return root;
    }

    void generics() {
        Map<String, Throwable> map = new HashMap<>();
        map.put("asdf", new Throwable());
    }

    void call(Exception e) {
        Throwable cause = e.getCause();
        String message = cause.getMessage();
        System.out.println("message = " + message);
    }
}

class FailTest {
    static RuntimeException fail() {
        throw new RuntimeException();
    }

    void testThrow(int x) {
        if(x < 0) {
            throw fail();
        }
    }
    void testNoThrow(int x) {
        if(x < 0) {
            fail();
        }
    }
}