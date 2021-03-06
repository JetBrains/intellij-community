import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

class Test {
  void test1() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getName().equals("equals")) {
          return params[0] == proxy;
        }
        if (method.getName().equals("hashCode")) {
          return 123;
        }
        System.out.println("oops");
        return <warning descr="Null might be returned when proxying method 'toString()': this is discouraged">null</warning>;
      });
  }

  public static void main(String[] args) {
    Runnable myProxy = (Runnable) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                                         new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getName().equals("run")) {
          System.out.println("Hello World!");
        }
        return <warning descr="Null might be returned when proxying method 'equals()': this may cause NullPointerException">null</warning>;
      });
    myProxy.run();
    HashSet<Runnable> set = new HashSet<>();
    set.add(myProxy);
  }

  void unused() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, <warning descr="Method is never used in 'invoke': it's unlikely that 'hashCode', 'equals' and 'toString' are implemented correctly">method</warning>, params) -> {
        System.out.println("Hello World!");
        return null;
      });

  }

  void testByMethodName() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getName().equals("run")) {
          System.out.println("Hello World!");
          return null;
        }
        return method.invoke(proxy, params);
      });
  }

  void testByObjectClassMistake() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
          if (method.getDeclaringClass() == Object.class) {
            System.out.println("Hello World!");
            return <warning descr="Null might be returned when proxying method 'equals()': this may cause NullPointerException">null</warning>;
          }
          return method.invoke(proxy, params);
        }
      });
  }
  
  void testByRunnableOk() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getDeclaringClass() == Runnable.class) {
          System.out.println("Hello World!");
          return null;
        }
        return method.invoke(proxy, params);
      });
  }

  void testByClassNameTypo() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getDeclaringClass().getName().equals("java.lang.Objec")) {
          return method.invoke(proxy, params);
        }
        System.out.println("oops");
        return <warning descr="Null might be returned when proxying method 'equals()': this may cause NullPointerException">null</warning>;
      });
  }
  
  void testByClassNameOk() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          return method.invoke(proxy, params);
        }
        System.out.println("oops");
        return null;
      });
  }

  void testByObjectMethodNamesOk() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getName().equals("equals")) {
          return params[0] == proxy;
        }
        if (method.getName().equals("hashCode")) {
          return 123;
        }
        if (method.getName().equals("toString")) {
          return "MyProxy!";
        }
        System.out.println("oops");
        return null;
      });
  }
  
  void testByMethodNameIncorrectType() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, (proxy, method, params) -> {
        if (method.getName().equals("equals") || method.getName().equals("hashCode")) {
          return <warning descr="Incompatible type might be returned when proxying method 'hashCode()': required: exactly Integer; got: exactly Boolean">params[0] == proxy</warning>;
        }
        if (method.getName().equals("toString")) {
          return "MyProxy!";
        }
        System.out.println("oops");
        return null;
      });
  }
  
  Object handlerUnused(Object proxy, Method m, Object[] args) {
    System.out.println("hello");
    return null;
  }
  
  Object handlerUsed(Object proxy, Method <warning descr="Method is never used in 'invoke': it's unlikely that 'hashCode', 'equals' and 'toString' are implemented correctly">m</warning>, Object[] args) {
    System.out.println("hello");
    return null;
  }
  
  static String handlerUsedStatic(Object proxy, Method m, Object[] args) {
    System.out.println("hello: "+m.getName());
    return <warning descr="Incompatible type might be returned when proxying method 'equals()': required: exactly Boolean; got: exactly String">m.getName()</warning>;
  }
  
  void use() {
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, this::handlerUsed);
    Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                           new Class[]{Runnable.class}, Test::handlerUsedStatic);
  }
}