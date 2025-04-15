package com.intellij.driver.impl;

import com.intellij.driver.model.*;
import com.intellij.driver.model.transport.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginContentDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.client.ClientKind;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin.text.StringsKt;
import org.apache.commons.lang3.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.driver.model.transport.RemoteCall.isPassByValue;
import static java.util.Objects.requireNonNull;

public class Invoker implements InvokerMBean {
  private static final Logger LOG = Logger.getInstance(Invoker.class);

  private static final int GLOBAL_SESSION_ID = 0;
  static final AtomicInteger REF_SEQUENCE = new AtomicInteger(1);

  private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
  private final AtomicInteger sessionIdSequence = new AtomicInteger(1);

  private final Map<String, WeakReference<Object>> adhocReferenceMap = new ConcurrentHashMap<>();

  private final ClearableLazyValue<IJTracer> tracer;
  private final Function<String, String> screenshotAction;
  private final Supplier<? extends Context> timedContextSupplier;

  private final RdTarget rdTarget;

  public Invoker(RdTarget rdTarget, @NotNull Supplier<? extends IJTracer> tracerSupplier,
                 @NotNull Supplier<? extends Context> timedContextSupplier,
                 @NotNull Function<String, String> screenshotAction) {
    this.rdTarget = rdTarget;
    this.timedContextSupplier = timedContextSupplier;
    this.tracer = new ClearableLazyValue<>() {
      @Override
      protected @NotNull IJTracer compute() {
        return tracerSupplier.get();
      }
    };
    this.screenshotAction = screenshotAction;
    sessions.put(GLOBAL_SESSION_ID, new GlobalSession());
  }

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
  public boolean isApplicationInitialized() {
    Application application = ApplicationManager.getApplication();
    return application != null && ((ApplicationEx)application).isComponentCreated();
  }

  @Override
  public void exit() {
    SwingUtilities.invokeLater(() -> {
      var app = ApplicationManager.getApplication();
      app.invokeLater(() -> app.exit(true, true, false), ModalityState.current());
    });
  }

  @Override
  public @NotNull RemoteCallResult invoke(@NotNull RemoteCall call) {
    Object[] transformedArgs = transformArgs(call);

    Object result;

    if (call instanceof NewInstanceCall) {
      Class<?> targetClass = getTargetClass(call);
      Constructor<?> constructor = getConstructor(call, targetClass, transformedArgs);

      LOG.debug("Creating instance of " + targetClass);

      result = withSemantics(call, () -> invokeConstructor(constructor, transformedArgs));

      return getRemoteCallResult(sessions.get(call.getSessionId()), result);
    }

    CallTarget callTarget = getCallTarget(call, transformedArgs);
    LOG.debug("Calling " + callTarget);

    Object instance;
    try {
      instance = findInstance(call, callTarget);
    }
    catch (Exception e) {
      //we need to ignore caching check and not throw error
      if (!call.getMethodName().equals("isShowing")) {
        LOG.error("Unable to get instance for " + call);
      }

      throw new DriverIllegalStateException("Unable to get instance for " + call, e);
    }

    if (call.getDispatcher() == OnDispatcher.EDT) {
      Object[] res = new Object[1];
      ModalityState[] modalityState = new ModalityState[1];
      try {
        SwingUtilities.invokeAndWait(() -> {
          modalityState[0] = ModalityState.current();
        });
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
      }
      Application app = ApplicationManager.getApplication();
      Runnable runnable = () -> {
        res[0] = withSemantics(call, () -> invokeMethod(callTarget, instance, transformedArgs));
      };
      if (call.getLockSemantics() == LockSemantics.NO_LOCK && app instanceof ApplicationEx applicationEx) {
        applicationEx.invokeAndWaitRelaxed(runnable, modalityState[0]);
      } else {
        app.invokeAndWait(runnable, modalityState[0]);
      }
      result = res[0];
    }
    else {
      // todo handle OnDispatcher: Default or IO
      result = withSemantics(call, () -> invokeMethod(callTarget, instance, transformedArgs));
    }

    return getRemoteCallResult(sessions.get(call.getSessionId()), callTarget, result);
  }

  @Override
  public @NotNull Ref putAdhocReference(@NotNull Object item) {
    return putAdhocReference(item, sessions.get(GLOBAL_SESSION_ID));
  }

  private @NotNull Object getReference(int sessionId, String id) {
    // first lookup in the current session
    Session session = sessions.get(sessionId);
    if (session == null) {
      throw new DriverIllegalStateException("No such session " + sessionId);
    }

    Object value = session.findReference(id);
    if (value != null) return value;

    // otherwise check any sessions
    for (Session s : sessions.values()) {
      value = s.findReference(id);
      if (value != null) return value;
    }

    throw new DriverIllegalStateException("No such reference with id " + id + ". " +
                                          "It may happen if a weak reference to the variable expires. " +
                                          "Please use `Driver.withContext { }` for hard variable references.");
  }

  private static @NotNull Object invokeConstructor(Constructor<?> constructor, Object[] transformedArgs) throws Exception {
    try {
      return constructor.newInstance(transformedArgs);
    }
    catch (IllegalArgumentException ie) {
      String message = "Argument type mismatch for constructor " + constructor + " , actual types are [" +
                       getExpectedTypesMessage(transformedArgs) + "]";
      LOG.warn(message, ie);

      throw new IllegalArgumentException(message, ie);
    }
    catch (Throwable e) {
      LOG.warn("Error during remote driver call " + constructor, e);

      throw e;
    }
  }

  private static @NotNull String getExpectedTypesMessage(Object[] transformedArgs) {
    return Arrays.stream(transformedArgs)
      .map(a -> {
        if (a == null) return "null";
        return a.getClass().getSimpleName();
      })
      .collect(Collectors.joining(", "));
  }

  private static Object invokeMethod(CallTarget callTarget, Object instance, Object[] args) throws Exception {
    try {
      return callTarget.targetMethod().invoke(instance, args);
    }
    catch (IllegalArgumentException ie) {
      String message = "Argument type mismatch for call " + callTarget.targetMethod() + ", actual types are [" +
                       getExpectedTypesMessage(args) + "]";
      LOG.warn(message, ie);

      throw new IllegalArgumentException(message, ie);
    }
    catch (Throwable e) {
      LOG.warn("Error during remote driver call " + callTarget.targetMethod(), e);

      throw e;
    }
  }

  private @Nullable Object withSemantics(@NotNull RemoteCall call, @NotNull Callable<?> supplier) {
    switch (call.getLockSemantics()) {
      case NO_LOCK -> {
        return call(call, supplier);
      }
      case READ_ACTION -> {
        return ReadAction.compute(() -> call(call, supplier));
      }
      case WRITE_ACTION -> {
        return WriteAction.compute(() -> call(call, supplier));
      }
      default -> throw new UnsupportedOperationException("Unsupported LockSemantics " + call.getLockSemantics());
    }
  }

  private @Nullable Object call(@NotNull RemoteCall call, @NotNull Callable<?> supplier) {
    if (call.getTimedSpan() == null || call.getTimedSpan().isEmpty()) {
      try {
        return supplier.call();
      }
      catch (InvocationTargetException e) {
        if (e.getCause() instanceof IllegalComponentStateException) {
          throw (IllegalComponentStateException)e.getCause();
        }

        throw new DriverIllegalStateException(e);
      }
      catch (Exception e) {
        ExceptionUtil.rethrow(e);
        throw new IllegalStateException();
      }
    }
    else {
      SpanBuilder spanBuilder = tracer.getValue().spanBuilder(call.getTimedSpan())
        .setParent(timedContextSupplier.get());

      Span span = spanBuilder.startSpan();
      try {
        return supplier.call();
      }
      catch (Exception e) {
        ExceptionUtil.rethrow(e);
        throw new IllegalStateException();
      }
      finally {
        span.end();
      }
    }
  }

  @Override
  public int newSession() {
    int id = sessionIdSequence.getAndIncrement();
    return newSession(id);
  }

  @Override
  public int newSession(int id) {
    sessions.put(id, new SessionImpl());
    return id;
  }

  private static Constructor<?> getConstructor(@NotNull RemoteCall call, @NotNull Class<?> targetClass, Object[] transformedArgs) {
    int argCount = call.getArgs().length;
    List<Constructor<?>> availableConstructors = Arrays.stream(targetClass.getConstructors()).toList();
    List<Constructor<?>> constructors = availableConstructors.stream()
      .filter(x -> x.getParameterCount() == argCount)
      .toList();

    if (constructors.isEmpty()) {
      throw new IllegalStateException(
        String.format("No constructor with parameter count %s in class %s. Available constructors: %n%s",
                      argCount, call.getClassName(),
                      availableConstructors.stream().map(it -> it.toString())
                        .collect(Collectors.joining(" - " + System.lineSeparator()))
        ));
    }

    if (constructors.size() > 1) {
      List<@Nullable Class<?>> argumentTypes = getArgumentTypes(transformedArgs);
      // take into account argument types
      for (Constructor<?> constructor : constructors) {
        if (areTypesCompatible(constructor.getParameterTypes(), argumentTypes)) {
          return constructor;
        }
      }
    }

    return constructors.get(0);
  }

  private @NotNull CallTarget getCallTarget(@NotNull RemoteCall call, Object[] transformedArgs) {
    Class<?> clazz = getTargetClass(call);

    int argCount = call.getArgs().length;

    List<Method> availableMethods = Arrays.stream(clazz.getMethods()).toList();

    if (call instanceof UtilityCall && isKotlinClass(clazz)) {
      Class<?> companionClass =
        ContainerUtil.find(clazz.getDeclaredClasses(), c -> c.getName().equals(call.getClassName() + "$Companion"));
      if (companionClass != null) {
        clazz = companionClass;
        availableMethods = availableMethods.stream().filter(m -> Modifier.isStatic(m.getModifiers())).toList();
        List<Method> companionNotStaticMethods = Arrays.stream(clazz.getMethods())
          .filter(m -> m.getAnnotation(JvmStatic.class) == null)
          .toList();
        availableMethods = ContainerUtil.concat(availableMethods, companionNotStaticMethods);
      }
    }

    List<Method> targetMethods = availableMethods.stream()
      .filter(m -> m.getName().equals(call.getMethodName()) && argCount == m.getParameterCount())
      .toList();

    if (targetMethods.isEmpty()) {
      StringBuilder message = new StringBuilder(
        String.format("No method '%s' with parameter count %s in class %s.", call.getMethodName(), argCount, call.getClassName())
      );
      if (call instanceof UtilityCall && isKotlinClass(clazz)) {
        message
          .append(System.lineSeparator())
          .append("For utility call only static methods were checked. If there is a companion object, its methods were also checked.");
      }
      message.append(
        String.format("\nAvailable methods: %n%s",
                      availableMethods.stream().map(it -> it.toString())
                        .collect(Collectors.joining(" - " + System.lineSeparator())))
      );
      throw new IllegalStateException(message.toString());
    }

    if (targetMethods.size() > 1) {
      List<@Nullable Class<?>> argumentTypes = getArgumentTypes(transformedArgs);
      // take into account argument types
      for (Method method : targetMethods) {
        if (areTypesCompatible(method.getParameterTypes(), argumentTypes)) {
          return buildCallTarget(clazz, method);
        }
      }
    }

    return buildCallTarget(clazz, targetMethods.get(0));
  }

  private static @NotNull CallTarget buildCallTarget(Class<?> clazz, Method method) {
    method.setAccessible(true);
    return new CallTarget(clazz, method);
  }

  private static @NotNull List<@Nullable Class<?>> getArgumentTypes(Object[] transformedArgs) {
    return Arrays.stream(transformedArgs)
      .map(a -> {
        if (a == null) return (Class<?>)null;
        return a.getClass();
      })
      .toList();
  }

  private static boolean areTypesCompatible(Class<?> @NotNull [] parameterTypes, @NotNull List<@Nullable Class<?>> argumentTypes) {
    for (int i = 0; i < argumentTypes.size(); i++) {
      Class<?> argType = argumentTypes.get(i);
      if (argType == null) continue;

      Class<?> parameterType = parameterTypes[i];
      if (!ClassUtils.isAssignable(argType, parameterType)) return false;
    }
    return true;
  }

  private @NotNull Class<?> getTargetClass(RemoteCall call) {
    Class<?> clazz;
    try {
      clazz = getClassLoader(call).loadClass(call.getClassName());
    }
    catch (ClassNotFoundException e) {
      throw new DriverIllegalStateException(
        (rdTarget == RdTarget.DEFAULT ? "" : rdTarget + ": ") + "No such class '" + call.getClassName() + "' in plugin " + call.getPluginId(), e);
    }
    return clazz;
  }

  private @NotNull ClassLoader getClassLoader(RemoteCall call) {
    String pluginId = call.getPluginId();
    if (pluginId == null || pluginId.isEmpty()) return getClass().getClassLoader();

    if (pluginId.contains("/")) {
      String mainId = StringsKt.substringBefore(pluginId, "/", pluginId);
      String moduleId = StringsKt.substringAfter(pluginId, "/", pluginId);

      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(mainId));
      if (plugin == null) throw new DriverIllegalStateException("No such plugin " + mainId);

      List<PluginContentDescriptor.ModuleItem> modules = ((IdeaPluginDescriptorImpl)plugin).getContent().modules;
      for (PluginContentDescriptor.ModuleItem module : modules) {
        if (Objects.equals(moduleId, module.name)) {
          return requireNonNull(module.requireDescriptor().getPluginClassLoader());
        }
      }

      throw new DriverIllegalStateException("No such plugin module " + pluginId);
    }

    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId));
    if (plugin == null) throw new DriverIllegalStateException("No such plugin " + pluginId);

    return plugin.getClassLoader();
  }

  private @Nullable Object findInstance(RemoteCall call, CallTarget callTarget) {
    Class<?> clazz = callTarget.clazz();
    if (call instanceof ServiceCall) {
      Object projectInstance = null;
      Ref projectRef = ((ServiceCall)call).getProjectRef();
      if (projectRef != null) {
        projectInstance = getReference(call.getSessionId(), projectRef.id());
      }

      Class<?> serviceClass = clazz;
      String serviceInterface = ((ServiceCall)call).getServiceInterface();
      if (serviceInterface != null) {
        serviceClass = findServiceInterface(clazz, serviceInterface);
        if (serviceClass == null) {
          throw new DriverIllegalStateException("Unable to find interface " + serviceInterface + " for service " + clazz);
        }
      }

      Object instance;
      if (projectInstance instanceof Project) {
        instance = ((Project)projectInstance).getService(serviceClass);
        if (instance == null) {
          instance = ((Project)projectInstance).getServices(serviceClass, ClientKind.CONTROLLER).get(0);
        }
      }
      else {
        instance = ApplicationManager.getApplication().getService(serviceClass);
        if (instance == null) {
          instance = ApplicationManager.getApplication().getServices(serviceClass, ClientKind.CONTROLLER).get(0);
        }
      }
      return instance;
    }

    if (call instanceof RefCall) {
      Ref ref = ((RefCall)call).getRef();
      Object reference = getReference(call.getSessionId(), ref.id());

      if (reference == null) throw new DriverIllegalStateException("No such ref exists " + ref);

      return reference;
    }

    if (call instanceof UtilityCall) {
      Object instance = null;
      int modifiers = callTarget.targetMethod().getModifiers();
      if (Modifier.isStatic(modifiers)) {
        return instance;
      }

      if(isKotlinClass(clazz)) {
        if (clazz.getName().endsWith("$Companion")) { //getting an instance of companion class
          try {
            instance = clazz.getEnclosingClass().getDeclaredField("Companion").get(null);
          }
          catch (NoSuchFieldException | IllegalAccessException e) {
            throw new DriverIllegalStateException(
              String.format("Failed to get an instance of a companion class %s", clazz.getName()), e
            );
          }
        }
        else { //getting an instance of Kotlin object
          try {
            instance = clazz.getDeclaredField("INSTANCE").get(null);
          }
          catch (NoSuchFieldException | IllegalAccessException e) {
            throw new DriverIllegalStateException(
              String.format("Failed to get an instance of a Kotlin object %s", clazz.getName()), e
            );
          }
        }
      }
      return instance;
    }

    throw new UnsupportedOperationException("Unsupported call type " + call);
  }

  private static boolean isKotlinClass(Class<?> clazz) {
    return clazz.getAnnotation(Metadata.class) != null;
  }

  private static @Nullable Class<?> findServiceInterface(@NotNull Class<?> clazz, @NotNull String serviceInterface) {
    for (Class<?> anInterface : clazz.getInterfaces()) {
      if (serviceInterface.equals(anInterface.getName())) {
        return anInterface;
      }
    }

    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null) {
      if (serviceInterface.equals(superclass.getName())) {
        return superclass;
      }

      return findServiceInterface(superclass, serviceInterface);
    }

    return null;
  }

  private Object @NotNull [] transformArgs(@NotNull RemoteCall call) {
    Object[] args = new Object[call.getArgs().length];
    for (int i = 0; i < args.length; i++) {
      var arg = call.getArgs()[i];
      args[i] = transformArg(call, arg);
    }

    return args;
  }

  private Object transformArg(@NotNull RemoteCall call, Object arg) {
    if (arg != null && arg.getClass().isArray() && Array.getLength(arg) > 0 && ContainerUtil.and((Object[])arg, item -> item instanceof Ref)) {
      var componentType = getReference(call.getSessionId(), ((Ref)Array.get(arg, 0)).id()).getClass();
      var result = Array.newInstance(componentType, Array.getLength(arg));
      for (int i = 0; i < Array.getLength(arg); i++) {
        Array.set(result, i, getReference(call.getSessionId(), ((Ref)Array.get(arg, i)).id()));
      }
      return result;
    }
    if (arg instanceof List<?> && !((List<?>)arg).isEmpty() && ContainerUtil.and(((List<?>)arg), item -> item instanceof Ref)) {
      return ContainerUtil.map(((List<?>) arg), item -> getReference(call.getSessionId(), ((Ref)item).id()));
    }
    if (arg instanceof Ref) {
      return getReference(call.getSessionId(), ((Ref)arg).id());
    }
    return arg;
  }

  private static @NotNull RemoteCallResult getRemoteCallResult(@NotNull Session session, @NotNull CallTarget callTarget, Object result) {
    Method targetMethod = callTarget.targetMethod();

    if (Collection.class.isAssignableFrom(targetMethod.getReturnType())) {
      Type returnType = targetMethod.getGenericReturnType();

      if (returnType instanceof ParameterizedType) {
        Type[] typeArguments = ((ParameterizedType)returnType).getActualTypeArguments();
        if (typeArguments.length == 1 && typeArguments[0] instanceof Class<?> componentType) {
          if (isPassByValue(componentType)) {
            return new RemoteCallResult(result);
          }
        }
      }
    }
    else if (targetMethod.getReturnType().isArray()) {
      if (isPassByValue(targetMethod.getReturnType().getComponentType())) {
        return new RemoteCallResult(result);
      }
    }

    return getRemoteCallResult(session, result);
  }

  private static @NotNull Object dereference(@NotNull WeakReference<Object> reference, String id) {
    Object weakTarget = reference.get();
    if (weakTarget == null) {
      throw new IllegalStateException(
        "Weak reference to variable " + id + " expired. " +
        "Please use `Driver.withContext { }` for hard variable references."
      );
    }
    return weakTarget;
  }

  private static @NotNull RemoteCallResult getRemoteCallResult(@NotNull Session session, Object result) {
    if (isPassByValue(result)) {
      // does not need a session, pass by value
      return new RemoteCallResult(result);
    }

    Ref ref = putAdhocReference(result, session);

    var stream =
      result instanceof Collection<?> collection ? collection.stream() :
      result.getClass().isArray() ? Arrays.stream((Object[])result) :
      null;

    if (stream == null) {
      return new RemoteCallResult(ref);
    }

    List<Ref> items = stream.map(item -> putAdhocReference(item, session)).toList();
    return new RemoteCallResult(new RefList(ref.id(), result.getClass().getName(), items));
  }

  private static @NotNull Ref putAdhocReference(@NotNull Object item, @NotNull Session session) {
    return session.putReference(item);
  }

  @Override
  public void cleanup(int sessionId) {
    sessions.remove(sessionId);

    var expiredKeys = adhocReferenceMap.entrySet().stream()
      .filter(entry -> entry.getValue().get() == null)
      .map(Map.Entry::getKey)
      .toList();

    for (String key : expiredKeys) {
      adhocReferenceMap.remove(key);
    }
  }

  @Override
  public String takeScreenshot(@Nullable String outFolder) {
    return this.screenshotAction.apply(outFolder);
  }

  private String genId() {
    return rdTarget.name() + "_" + REF_SEQUENCE.getAndIncrement();
  }

  interface Session {
    @Nullable
    Object findReference(String id);

    @NotNull
    Ref putReference(@NotNull Object value);
  }

  final class SessionImpl implements Session {
    private final Map<String, HardReference> variables = new ConcurrentHashMap<>();

    @Override
    public @Nullable Object findReference(String id) {
      HardReference ref = variables.get(id);
      if (ref == null) return null;

      return ref.value;
    }

    @Override
    public @NotNull Ref putReference(@NotNull Object value) {
      var id = genId();
      variables.put(id, new HardReference(value));

      // also make variable available out ouf `driver.withContext { }` block as weak reference
      adhocReferenceMap.put(id, new WeakReference<>(value));

      return RefProducer.makeRef(id, rdTarget, value);
    }
  }

  final class GlobalSession implements Session {
    @Override
    public @Nullable Object findReference(String id) {

      WeakReference<Object> reference = adhocReferenceMap.get(id);
      if (reference == null) return null;

      return dereference(reference, id);
    }

    @Override
    public @NotNull Ref putReference(@NotNull Object value) {
      var id = genId();
      adhocReferenceMap.put(id, new WeakReference<>(value));

      return RefProducer.makeRef(id, rdTarget, value);
    }
  }
}

record CallTarget(@NotNull Class<?> clazz, @NotNull Method targetMethod) {
}

final class HardReference {
  final Object value;

  HardReference(Object value) { this.value = value; }
}

final class RefProducer {
  public static @NotNull Ref makeRef(String id, RdTarget rdTarget, @NotNull Object value) {
    if (value instanceof Ref) return (Ref)value;

    return new Ref(
      id,
      value.getClass().getName(),
      System.identityHashCode(value),
      value.toString(),
      rdTarget
    );
  }
}
