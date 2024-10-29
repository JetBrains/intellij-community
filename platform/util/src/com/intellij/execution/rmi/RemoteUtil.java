// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi;

import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.rmi.Remote;
import java.rmi.ServerError;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Gregory.Shrago
 */
@ApiStatus.Internal
public final class RemoteUtil {
  RemoteUtil() {
  }

  private static final ConcurrentMap<Couple<Class<?>>, Map<Method, Method>> ourRemoteToLocalMap =
    ConcurrentFactoryMap.createMap(key -> {
      Map<Method, Method> map = new HashMap<>();
      for (Method method : key.second.getMethods()) {
        Method m = null;
        main:
        for (Method candidate : key.first.getMethods()) {
          if (candidate.getParameterCount() != method.getParameterCount()) continue;
          if (!candidate.getName().equals(method.getName())) continue;
          Class<?>[] cpts = candidate.getParameterTypes();
          Class<?>[] mpts = method.getParameterTypes();
          for (int i = 0; i < mpts.length; i++) {
            Class<?> mpt = mpts[i];
            Class<?> cpt = castArgumentClassToLocal(cpts[i]);
            if (!cpt.isAssignableFrom(mpt)) continue main;
          }
          m = candidate;
          break;
        }
        if (m != null) map.put(method, m);
      }
      return map;
    });

  public static @NotNull <T> T castToRemoteNotNull(Object object, Class<T> clazz) {
    return Objects.requireNonNull(castToRemote(object, clazz));
  }

  public static @Nullable <T> T castToRemote(@Nullable Object object, @NotNull Class<T> clazz) {
    if (object == null || !Proxy.isProxyClass(object.getClass())) return null;
    if (clazz.isInstance(object)) return (T)object;
    final InvocationHandler handler = Proxy.getInvocationHandler(object);
    if (handler instanceof RemoteInvocationHandler) {
      final RemoteInvocationHandler rih = (RemoteInvocationHandler)handler;
      if (clazz.isInstance(rih.myRemote)) {
        return (T)rih.myRemote;
      }
    }
    return null;
  }

  public static @NotNull <T> T castToLocal(@Nullable Object remote, @NotNull Class<T> clazz) {
    if (clazz.isInstance(remote)) return clazz.cast(remote);
    ClassLoader loader = clazz.getClassLoader();
    return ReflectionUtil.proxy(clazz, new RemoteInvocationHandler(remote, clazz, loader));
  }

  private static Class<?> tryFixReturnType(Object result, Class<?> returnType, ClassLoader loader) throws Exception {
    if (returnType.isInterface()) return returnType;
    if (result instanceof RemoteCastable) {
      final String className = ((RemoteCastable)result).getCastToClassName();
      return Class.forName(className, true, loader);
    }
    return returnType;
  }

  private static Class<?> castArgumentClassToLocal(@NotNull Class<?> remote) {
    try {
      if (!CastableArgument.class.isAssignableFrom(remote)) return remote;
      Type[] generics = remote.getGenericInterfaces();
      for (Type generic : generics) {
        if (generic instanceof ParameterizedType) {
          Type rawType = ((ParameterizedType)generic).getRawType();
          if (rawType == CastableArgument.class) return (Class<?>)((ParameterizedType)generic).getActualTypeArguments()[0];
        }
      }
    }
    catch (Exception ignore) {
    }
    return remote;
  }

  private static Object @Nullable [] fixArgs(Object @Nullable [] args, @NotNull Method method) {
    if (args == null) return null;
    if (method.getParameterCount() != args.length) return args;
    Object[] result = new Object[args.length];
    try {
      Class<?>[] methodArgs = method.getParameterTypes();
      for (int i = 0; i < args.length; i++) {
        result[i] = fixArg(args[i], methodArgs[i]);
      }
    }
    catch (Exception e) {
      return args;
    }
    return result;
  }

  private static @Nullable Object fixArg(@Nullable Object arg, @NotNull Class<?> fieldClass) {
    if (arg == null) return null;
    if (!fieldClass.isPrimitive() && Proxy.isProxyClass(arg.getClass())) {
      InvocationHandler handler = Proxy.getInvocationHandler(arg);
      RemoteInvocationHandler remoteHandler = ObjectUtils.tryCast(handler, RemoteInvocationHandler.class);
      boolean isCastableArg = remoteHandler != null && CastableArgument.class.isAssignableFrom(remoteHandler.myRemote.getClass());
      if (isCastableArg && remoteHandler.myClazz == fieldClass) return remoteHandler.myRemote;
    }
    return arg;
  }

  public static <T> T substituteClassLoader(final @NotNull T remote, final @Nullable ClassLoader classLoader) throws Exception {
    final class MyHandler implements InvocationHandler {
      @Override
      public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        if ("equals".equals(method.getName())) {
          Object arg = args.length == 1 ? args[0] : null;
          if (arg != null && Proxy.isProxyClass(arg.getClass()) && Proxy.getInvocationHandler(arg) instanceof MyHandler) {
            return arg.equals(remote);
          }
        }
        return executeWithClassLoader(() -> invokeRemote(method, method, remote, args, classLoader, true), classLoader);
      }
    }
    return executeWithClassLoader(() -> {
      Object proxy = Proxy.newProxyInstance(classLoader, remote.getClass().getInterfaces(), new MyHandler());
      return (T)proxy;
    }, classLoader);
  }

  private static Object invokeRemote(@NotNull Method localMethod,
                                     @NotNull Method remoteMethod,
                                     @NotNull Object remoteObj,
                                     Object @Nullable [] args,
                                     @Nullable ClassLoader loader,
                                     boolean substituteClassLoader)
    throws Exception {
    boolean canThrowError = false;
    try {
      Object result = remoteMethod.invoke(remoteObj, args);
      canThrowError = true;
      return handleRemoteResult(result, localMethod.getReturnType(), loader, substituteClassLoader);
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause(); // root cause may go deeper than we need, so leave it like this
      if (cause instanceof ServerError) cause = ObjectUtils.chooseNotNull(cause.getCause(), cause);
      if (cause instanceof RuntimeException) throw (RuntimeException)cause;
      else if (canThrowError && cause instanceof Error || cause instanceof LinkageError) throw (Error)cause;
      else if (cause instanceof Exception && canThrow(cause, localMethod)) throw (Exception)cause;
      throw new RuntimeException(cause);
    }
  }

  public static <T> T handleRemoteResult(Object value, Class<? super T> clazz, Object requestor) throws Exception {
    return handleRemoteResult(value, clazz, requestor.getClass().getClassLoader(), false);
  }

  private static <T> T handleRemoteResult(Object value, Class<?> methodReturnType, ClassLoader classLoader, boolean substituteClassLoader) throws Exception {
    Object result;
    if (value instanceof Remote) {
      if (value instanceof RemoteCastable) {
        result = castToLocal(value, tryFixReturnType(value, methodReturnType, classLoader));
      }
      else {
        result = substituteClassLoader? substituteClassLoader(value, classLoader) : value;
      }
    }
    else if (value instanceof List && methodReturnType.isInterface()) {
      result = Arrays.asList(handleRemoteResult(((List<?>)value).toArray(), Object.class, classLoader, substituteClassLoader));
    }
    else if (value instanceof Object[]) {
      Object[] array = (Object[])value;
      for (int i = 0; i < array.length; i++) {
         array[i] = handleRemoteResult(array[i], Object.class, classLoader, substituteClassLoader);
      }
      result = array;
    }
    else {
      result = value;
    }
    return (T)result;
  }

  private static boolean canThrow(@NotNull Throwable cause, @NotNull Method method) {
    for (Class<?> each : method.getExceptionTypes()) {
      if (each.isInstance(cause)) return true;
    }
    return false;
  }

  public static <T> T executeWithClassLoader(ThrowableComputable<T, ? extends Exception> action, ClassLoader classLoader) throws Exception {
    return ClassLoaderUtil.computeWithClassLoader(classLoader, action);
  }

  /**
   * There is a possible case that remotely called code throws exception during processing. That exception is wrapped at different
   * levels then - {@link InvocationTargetException}, {@link UndeclaredThrowableException} etc.
   * <p/>
   * This method tries to extract the 'real exception' from the given potentially wrapped one.
   *
   * @param e  exception to process
   * @return   extracted 'real exception' if any; given exception otherwise
   */
  public static @NotNull Throwable unwrap(@NotNull Throwable e) {
    for (Throwable candidate = e; candidate != null; candidate = candidate.getCause()) {
      Class<? extends Throwable> clazz = candidate.getClass();
      if (clazz != InvocationTargetException.class && clazz != UndeclaredThrowableException.class) {
        return candidate;
      }
    }
    return e;
  }

  private static final class RemoteInvocationHandler implements InvocationHandler {
    private final Object myRemote;
    private final Class<?> myClazz;
    private final ClassLoader myLoader;

    RemoteInvocationHandler(Object remote, Class<?> clazz, ClassLoader loader) {
      myRemote = remote;
      myClazz = clazz;
      myLoader = loader;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        if ("equals".equals(method.getName())) return proxy == args[0];
        if ("hashCode".equals(method.getName())) return hashCode();
        return method.invoke(myRemote, args);
      }
      else {
        Method remoteMethod = ourRemoteToLocalMap.get(Couple.of(myRemote.getClass(), myClazz)).get(method);
        if (remoteMethod == null) throw new NoSuchMethodError(method.getName() + " in " + myRemote.getClass());
        return invokeRemote(method, remoteMethod, myRemote, fixArgs(args, method), myLoader, false);
      }
    }
  }
}
