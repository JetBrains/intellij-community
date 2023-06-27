package com.intellij.driver.impl;

import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.ProductVersion;
import com.intellij.driver.model.transport.Ref;
import com.intellij.driver.model.transport.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.driver.model.transport.RemoteCall.isPassByValue;

public class Invoker implements InvokerMBean {
  public static final int NO_SESSION_ID = 0;

  private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
  private final AtomicInteger sessionIdSequence = new AtomicInteger(1);

  private final Map<Integer, WeakReference<Object>> adhocReferenceMap = new ConcurrentHashMap<>();
  static final AtomicInteger REF_SEQUENCE = new AtomicInteger(1);

  @Override
  public ProductVersion getProductVersion() {
    BuildNumber build = ApplicationInfoImpl.getShadowInstanceImpl().getBuild();

    return new ProductVersion(
      build.getProductCode(),
      build.isSnapshot(),
      build.getBaselineVersion(),
      build.asString()
    );
  }

  @Override
  public @NotNull RemoteCallResult invoke(@NotNull RemoteCall call) {
    Object[] args = transformArgs(call);

    Object result;

    if (call instanceof NewInstanceCall) {
      Class<?> targetClass = getTargetClass(call);
      Constructor<?> constructor = getConstructor(call, targetClass);

      result = withSemantics(() -> constructor.newInstance(args), call.getLockSemantics());
    }
    else {
      CallTarget callTarget = getCallTarget(call);
      Object instance = findInstance(call, callTarget.clazz());

      if (call.getDispatcher() == OnDispatcher.EDT) {
        Object[] res = new Object[1];
        ApplicationManager.getApplication().invokeAndWait(() -> {
          res[0] = withSemantics(() -> callTarget.targetMethod().invoke(instance, args), call.getLockSemantics());
        });
        result = res[0];
      }
      else {
        // todo handle OnDispatcher: Default or IO
        result = withSemantics(() -> callTarget.targetMethod().invoke(instance, args), call.getLockSemantics());
      }
    }

    if (isPassByValue(result)) {
      // does not need a session, pass by value
      return new RemoteCallResult(result);
    }

    if (call.getSessionId() == NO_SESSION_ID) {
      int id = REF_SEQUENCE.getAndIncrement();
      Ref ref = RefProducer.makeRef(id, result);
      adhocReferenceMap.put(id, new WeakReference<>(result));

      if (result instanceof Collection<?>) {
        List<Ref> items = new ArrayList<>(((Collection<?>)result).size());
        for (Object item : ((Collection<?>)result)) {
          items.add(putAdhocReference(item));
        }
        return new RemoteCallResult(new RefList(id, result.getClass().getName(), items));
      }

      if (result.getClass().isArray()) {
        Object[] array = (Object[])result;

        List<Ref> items = new ArrayList<>(array.length);
        for (Object item : array) {
          items.add(putAdhocReference(item));
        }
        return new RemoteCallResult(new RefList(id, result.getClass().getName(), items));
      }

      return new RemoteCallResult(ref);
    }
    else {
      Session session = sessions.get(call.getSessionId());
      Ref ref = session.putReference(result);

      if (result instanceof Collection<?>) {
        List<Ref> items = new ArrayList<>(((Collection<?>)result).size());
        for (Object item : ((Collection<?>)result)) {
          items.add(session.putReference(item));
        }
        return new RemoteCallResult(new RefList(ref.id(), result.getClass().getName(), items));
      }

      if (result.getClass().isArray()) {
        Object[] array = (Object[])result;

        List<Ref> items = new ArrayList<>(array.length);
        for (Object item : array) {
          items.add(session.putReference(item));
        }
        return new RemoteCallResult(new RefList(ref.id(), result.getClass().getName(), items));
      }

      return new RemoteCallResult(ref);
    }
  }

  private static @Nullable Object withSemantics(@NotNull Callable<?> supplier, @NotNull LockSemantics semantics) {
    switch (semantics) {
      case NO_LOCK -> {
        return call(supplier);
      }
      case READ_ACTION -> {
        return ReadAction.compute(() -> call(supplier));
      }
      case WRITE_ACTION -> {
        return WriteAction.compute(() -> call(supplier));
      }
      default -> throw new UnsupportedOperationException("Unsupported LockSemantics " + semantics);
    }
  }

  private static @Nullable Object call(@NotNull Callable<?> supplier) {
    try {
      return supplier.call();
    }
    catch (Exception e) {
      throw new RuntimeException("Exception during call", e);
    }
  }

  private @NotNull Ref putAdhocReference(@NotNull Object item) {
    int itemId = REF_SEQUENCE.getAndIncrement();
    adhocReferenceMap.put(itemId, new WeakReference<>(item));
    return RefProducer.makeRef(itemId, item);
  }

  private static Constructor<?> getConstructor(@NotNull RemoteCall call, @NotNull Class<?> targetClass) {
    int argCount = call.getArgs().length;
    return Arrays.stream(targetClass.getConstructors())
      .filter(x -> x.getParameterCount() == argCount)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No such constructor found in " + targetClass));
  }

  private @NotNull CallTarget getCallTarget(@NotNull RemoteCall call) {
    Class<?> clazz = getTargetClass(call);

    int argCount = call.getArgs().length;
    Method targetMethod = Arrays.stream(clazz.getMethods())
      .filter(m -> m.getName().equals(call.getMethodName()) && argCount == m.getParameterCount())
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(
        "No method " + call.getMethodName() +
        " with parameter count " + argCount +
        " in class " + call.getClassName()));
    // todo take into account argument types

    return new CallTarget(clazz, targetMethod);
  }

  private Class<?> getTargetClass(RemoteCall call) {
    Class<?> clazz;
    try {
      clazz = getClassLoader(call).loadClass(call.getClassName());
    }
    catch (ClassNotFoundException e) {
      throw new IllegalStateException("No such class '" + call.getClassName() + "'", e);
    }
    return clazz;
  }

  private ClassLoader getClassLoader(RemoteCall call) {
    String pluginId = call.getPluginId();
    if (pluginId == null || pluginId.isEmpty()) return getClass().getClassLoader();

    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId));
    if (plugin == null) throw new IllegalStateException("No such plugin " + pluginId);

    return plugin.getClassLoader();
  }

  private Object findInstance(RemoteCall call, Class<?> clazz) {
    if (call instanceof ServiceCall) {
      Object projectInstance = null;
      Ref projectRef = ((ServiceCall)call).getProjectRef();
      if (projectRef != null) {
        projectInstance = getReference(call.getSessionId(), projectRef.id());
      }

      Object instance;
      if (projectInstance instanceof Project) { // todo assert
        instance = ((Project)projectInstance).getService(clazz);
      }
      else {
        instance = ApplicationManager.getApplication().getService(clazz);
      }
      return instance;
    }

    if (call instanceof RefCall) {
      Ref ref = ((RefCall)call).getRef();
      Object reference = getReference(call.getSessionId(), ref.id());

      if (reference == null) throw new IllegalStateException("No such ref exists " + ref);

      return reference;
    }

    if (call instanceof UtilityCall) {
      return null;
    }

    throw new UnsupportedOperationException("Unsupported call type " + call);
  }

  private Object @NotNull [] transformArgs(@NotNull RemoteCall call) {
    Object[] args = new Object[call.getArgs().length];
    for (int i = 0; i < args.length; i++) {
      var arg = call.getArgs()[i];

      if (arg instanceof Ref) {
        Object reference = getReference(call.getSessionId(), ((Ref)arg).id());
        args[i] = reference;
      }
      else {
        args[i] = arg;
      }
    }

    return args;
  }

  private @NotNull Object getReference(int sessionId, int id) {
    if (sessionId != NO_SESSION_ID) {
      WeakReference<Object> adhocReference = adhocReferenceMap.get(id);
      if (adhocReference != null) {
        return dereference(adhocReference, id);
      }

      // first lookup in session
      Session session = sessions.get(sessionId);
      if (session == null) {
        throw new IllegalStateException("No such session " + sessionId);
      }

      return session.findReference(id);
    }

    WeakReference<Object> reference = adhocReferenceMap.get(id);
    return dereference(reference, id);
  }

  private static @NotNull Object dereference(@NotNull WeakReference<Object> reference, int id) {
    Object weakTarget = reference.get();
    if (weakTarget == null) {
      throw new IllegalStateException(
        "Weak reference to variable " + id + " expired. " +
        "Please use `Driver.withContext { }` for hard variable references."
      );
    }
    return weakTarget;
  }

  @Override
  public int newSession() {
    int id = sessionIdSequence.getAndIncrement();
    sessions.put(id, new Session());
    return id;
  }

  @Override
  public void cleanup(int sessionId) {
    sessions.remove(sessionId);
  }
}

record CallTarget(@NotNull Class<?> clazz, @NotNull Method targetMethod) {
}

final class Session {
  private final Map<Integer, Object> variables = new ConcurrentHashMap<>();

  public @NotNull Object findReference(int id) {
    if (!variables.containsKey(id)) throw new IllegalStateException("No such reference with id " + id);
    return variables.get(id);
  }

  public @NotNull Ref putReference(@NotNull Object value) {
    var id = Invoker.REF_SEQUENCE.getAndIncrement();
    variables.put(id, value);
    return RefProducer.makeRef(id, value);
  }
}

final class RefProducer {
  public static @NotNull Ref makeRef(int id, @NotNull Object value) {
    if (value instanceof Ref) return (Ref)value;

    return new Ref(
      id,
      value.getClass().getName(),
      System.identityHashCode(value),
      value.toString()
    );
  }
}