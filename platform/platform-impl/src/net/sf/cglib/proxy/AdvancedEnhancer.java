/*
 * Copyright 2002,2003,2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.cglib.proxy;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import net.sf.cglib.asm.$ClassVisitor;
import net.sf.cglib.asm.$Label;
import net.sf.cglib.asm.$Type;
import net.sf.cglib.core.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Generates dynamic subclasses to enable method interception. This
 * class started as a substitute for the standard Dynamic Proxy support
 * included with JDK 1.3, but one that allowed the proxies to extend a
 * concrete base class, in addition to implementing interfaces. The dynamically
 * generated subclasses override the non-final methods of the superclass and
 * have hooks which callback to user-defined interceptor
 * implementations.
 * <p>
 * The original and most general callback type is the {@link MethodInterceptor}, which
 * in AOP terms enables "around advice"--that is, you can invoke custom code both before
 * and after the invocation of the "super" method. In addition you can modify the
 * arguments before calling the super method, or not call it at all.
 * <p>
 * Although {@code MethodInterceptor} is generic enough to meet any
 * interception need, it is often overkill. For simplicity and performance, additional
 * specialized callback types, such as {@link LazyLoader} are also available.
 * Often a single callback will be used per enhanced class, but you can control
 * which callback is used on a per-method basis with a {@link CallbackFilter}.
 * <p>
 * The most common uses of this class are embodied in the static helper methods. For
 * advanced needs, such as customizing the {@code ClassLoader} to use, you should create
 * a new instance of {@code Enhancer}. Other classes within CGLIB follow a similar pattern.
 * <p>
 * All enhanced objects implement the {@link Factory} interface, unless {@link #setUseFactory} is
 * used to explicitly disable this feature. The {@code Factory} interface provides an API
 * to change the callbacks of an existing object, as well as a faster and easier way to create
 * new instances of the same type.
 * <p>
 * For an almost drop-in replacement for
 * {@code java.lang.reflect.Proxy}, see the {@link Proxy} class.
 */

@SuppressWarnings("StaticFieldReferencedViaSubclass")
public class AdvancedEnhancer extends AbstractClassGenerator
{
  private static final CallbackFilter ALL_ZERO = new CallbackFilter(){
    public int accept(Method method) {
      return 0;
    }
  };

  private static final Source SOURCE = new Source(Enhancer.class.getName());

  private static final String BOUND_FIELD = "CGLIB$BOUND";
  private static final String THREAD_CALLBACKS_FIELD = "CGLIB$THREAD_CALLBACKS";
  private static final String STATIC_CALLBACKS_FIELD = "CGLIB$STATIC_CALLBACKS";
  private static final String SET_THREAD_CALLBACKS_NAME = "CGLIB$SET_THREAD_CALLBACKS";
  private static final String SET_STATIC_CALLBACKS_NAME = "CGLIB$SET_STATIC_CALLBACKS";
  private static final String CONSTRUCTED_FIELD = "CGLIB$CONSTRUCTED";

  private static final $Type FACTORY =
    TypeUtils.parseType("net.sf.cglib.proxy.Factory");
  private static final $Type ILLEGAL_STATE_EXCEPTION =
    TypeUtils.parseType("IllegalStateException");
  private static final $Type ILLEGAL_ARGUMENT_EXCEPTION =
    TypeUtils.parseType("IllegalArgumentException");
  private static final $Type THREAD_LOCAL =
    TypeUtils.parseType("ThreadLocal");
  private static final $Type CALLBACK =
    TypeUtils.parseType("net.sf.cglib.proxy.Callback");
  private static final $Type CALLBACK_ARRAY =
    $Type.getType(Callback[].class);
  private static final Signature CSTRUCT_NULL =
    TypeUtils.parseConstructor("");
  private static final Signature SET_THREAD_CALLBACKS =
    new Signature(SET_THREAD_CALLBACKS_NAME, $Type.VOID_TYPE, new $Type[]{ CALLBACK_ARRAY });
  private static final Signature SET_STATIC_CALLBACKS =
    new Signature(SET_STATIC_CALLBACKS_NAME, $Type.VOID_TYPE, new $Type[]{ CALLBACK_ARRAY });
  private static final Signature NEW_INSTANCE =
    new Signature("newInstance", Constants.TYPE_OBJECT, new $Type[]{ CALLBACK_ARRAY });
  private static final Signature MULTIARG_NEW_INSTANCE =
    new Signature("newInstance", Constants.TYPE_OBJECT, new $Type[]{
      Constants.TYPE_CLASS_ARRAY,
      Constants.TYPE_OBJECT_ARRAY,
      CALLBACK_ARRAY,
    });
  private static final Signature SINGLE_NEW_INSTANCE =
    new Signature("newInstance", Constants.TYPE_OBJECT, new $Type[]{ CALLBACK });
  private static final Signature SET_CALLBACK =
    new Signature("setCallback", $Type.VOID_TYPE, new $Type[]{ $Type.INT_TYPE, CALLBACK });
  private static final Signature GET_CALLBACK =
    new Signature("getCallback", CALLBACK, new $Type[]{ $Type.INT_TYPE });
  private static final Signature SET_CALLBACKS =
    new Signature("setCallbacks", $Type.VOID_TYPE, new $Type[]{ CALLBACK_ARRAY });
  private static final Signature GET_CALLBACKS =
    new Signature("getCallbacks", CALLBACK_ARRAY, new $Type[0]);
  private static final Signature THREAD_LOCAL_GET =
    TypeUtils.parseSignature("Object get()");
  private static final Signature THREAD_LOCAL_SET =
    TypeUtils.parseSignature("void set(Object)");
  private static final Signature BIND_CALLBACKS =
    TypeUtils.parseSignature("void CGLIB$BIND_CALLBACKS(Object)");
  private static final DefaultNamingPolicy JETBRAINS_NAMING_POLICY = new DefaultNamingPolicy() {
    @Override
    protected String getTag() {
      return "ByJetBrainsMainCglib";
    }
  };

  private Class[] interfaces;
  private CallbackFilter filter;
  private Callback[] callbacks;
  private $Type[] callbackTypes;
  private boolean classOnly;
  private Class superclass;
  private Class[] argumentTypes;
  private Object[] arguments;
  private boolean useFactory = true;
  private boolean interceptDuringConstruction = true;

  /**
   * Create a new {@code Enhancer}. A new {@code Enhancer}
   * object should be used for each generated object, and should not
   * be shared across threads. To create additional instances of a
   * generated class, use the {@code Factory} interface.
   * @see Factory
   */
  public AdvancedEnhancer() {
    super(SOURCE);
    // to distinguish from assertj, mockito and others that have copy of cglib inside and load their own classes with the same names but different super classes
    setNamingPolicy(JETBRAINS_NAMING_POLICY);
  }

  /**
   * Set the class which the generated class will extend. As a convenience,
   * if the supplied superclass is actually an interface, {@code setInterfaces}
   * will be called with the appropriate argument instead.
   * A non-interface argument must not be declared as final, and must have an
   * accessible constructor.
   * @param superclass class to extend or interface to implement
   * @see #setInterfaces(Class[])
   */
  public void setSuperclass(Class superclass) {
    if (superclass != null && superclass.isInterface()) {
      setInterfaces(new Class[]{ superclass });
    } else if (superclass != null && superclass.equals(Object.class)) {
      // affects choice of ClassLoader
      this.superclass = null;
    } else {
      this.superclass = superclass;
    }
  }

  /**
   * Set the interfaces to implement. The {@code Factory} interface will
   * always be implemented regardless of what is specified here.
   * @param interfaces array of interfaces to implement, or null
   * @see Factory
   */
  public void setInterfaces(Class[] interfaces) {
    this.interfaces = interfaces;
  }

  /**
   * Set the {@link CallbackFilter} used to map the generated class' methods
   * to a particular callback index.
   * New object instances will always use the same mapping, but may use different
   * actual callback objects.
   * @param filter the callback filter to use when generating a new class
   * @see #setCallbacks
   */
  public void setCallbackFilter(CallbackFilter filter) {
    this.filter = filter;
  }


  /**
   * Set the single {@link Callback} to use.
   * Ignored if you use {@link #createClass}.
   * @param callback the callback to use for all methods
   * @see #setCallbacks
   */
  public void setCallback(final Callback callback) {
    setCallbacks(new Callback[]{ callback });
  }

  /**
   * Set the array of callbacks to use.
   * Ignored if you use {@link #createClass}.
   * You must use a {@link CallbackFilter} to specify the index into this
   * array for each method in the proxied class.
   * @param callbacks the callback array
   * @see #setCallbackFilter
   * @see #setCallback
   */
  public void setCallbacks(Callback[] callbacks) {
    if (callbacks != null && callbacks.length == 0) {
      throw new IllegalArgumentException("Array cannot be empty");
    }
    this.callbacks = callbacks;
  }

  /**
   * Set whether the enhanced object instances should implement
   * the {@link Factory} interface.
   * This was added for tools that need for proxies to be more
   * indistinguishable from their targets. Also, in some cases it may
   * be necessary to disable the {@code Factory} interface to
   * prevent code from changing the underlying callbacks.
   * @param useFactory whether to implement {@code Factory}; default is {@code true}
   */
  public void setUseFactory(boolean useFactory) {
    this.useFactory = useFactory;
  }

  /**
   * Set whether methods called from within the proxy's constructer
   * will be intercepted. The default value is true. Unintercepted methods
   * will call the method of the proxy's base class, if it exists.
   * @param interceptDuringConstruction whether to intercept methods called from the constructor
   */
  public void setInterceptDuringConstruction(boolean interceptDuringConstruction) {
    this.interceptDuringConstruction = interceptDuringConstruction;
  }

  /**
   * Set the array of callback types to use.
   * This may be used instead of {@link #setCallbacks} when calling
   * {@link #createClass}, since it may not be possible to have
   * an array of actual callback instances.
   * You must use a {@link CallbackFilter} to specify the index into this
   * array for each method in the proxied class.
   * @param callbackTypes the array of callback types
   */
  public void setCallbackTypes(Class[] callbackTypes) {
    if (callbackTypes != null && callbackTypes.length == 0) {
      throw new IllegalArgumentException("Array cannot be empty");
    }
    this.callbackTypes = CallbackInfo.determineTypes(callbackTypes);
  }

  /**
   * Generate a new class if necessary and uses the specified
   * callbacks (if any) to create a new object instance.
   * Uses the no-arg constructor of the superclass.
   * @return a new instance
   */
  public Object create() {
    classOnly = false;
    argumentTypes = null;
    return createHelper();
  }

  /**
   * Generate a new class if necessary and uses the specified
   * callbacks (if any) to create a new object instance.
   * Uses the constructor of the superclass matching the {@code argumentTypes}
   * parameter, with the given arguments.
   * @param argumentTypes constructor signature
   * @param arguments compatible wrapped arguments to pass to constructor
   * @return a new instance
   */
  public Object create(Class[] argumentTypes, Object[] arguments) {
    classOnly = false;
    if (argumentTypes == null || arguments == null || argumentTypes.length != arguments.length) {
      throw new IllegalArgumentException("Arguments must be non-null and of equal length");
    }
    synchronized (this) {
      this.argumentTypes = argumentTypes;
      this.arguments = arguments;
      try {
        return createHelper();
      }
      finally {
        this.arguments = null;
      }
    }
  }

  private void validate() {
    if (classOnly ^ (callbacks == null)) {
      if (classOnly) {
        throw new IllegalStateException("createClass does not accept callbacks");
      } else {
        throw new IllegalStateException("Callbacks are required");
      }
    }
    if (classOnly && (callbackTypes == null)) {
      throw new IllegalStateException("Callback types are required");
    }
    if (callbacks != null && callbackTypes != null) {
      if (callbacks.length != callbackTypes.length) {
        throw new IllegalStateException("Lengths of callback and callback types array must be the same");
      }
      $Type[] check = CallbackInfo.determineTypes(callbacks);
      for (int i = 0; i < check.length; i++) {
        if (!check[i].equals(callbackTypes[i])) {
          throw new IllegalStateException("Callback " + check[i] + " is not assignable to " + callbackTypes[i]);
        }
      }
    } else if (callbacks != null) {
      callbackTypes = CallbackInfo.determineTypes(callbacks);
    }
    if (filter == null) {
      if (callbackTypes.length > 1) {
        throw new IllegalStateException("Multiple callback types possible but no filter specified");
      }
      filter = ALL_ZERO;
    }
    if (interfaces != null) {
      for (Class anInterface : interfaces) {
        if (anInterface == null) {
          throw new IllegalStateException("Interfaces cannot be null");
        }
        if (!anInterface.isInterface()) {
          throw new IllegalStateException(anInterface + " is not an interface");
        }
      }
    }
  }

  private Object createHelper() {
    validate();
    if (superclass != null) {
      setNamePrefix(superclass.getName());
    } else if (interfaces != null) {
      setNamePrefix(interfaces[ReflectUtils.findPackageProtected(interfaces)].getName());
    }
    return super.create(createKey());
  }

  @NotNull
  private List<Object> createKey() {
    List<Object> tuple = ContainerUtil.newArrayList(Arrays.asList(callbackTypes), (useFactory ? 1 : 0) + (interceptDuringConstruction ? 2 : 0));
    if (superclass != null) tuple.add(superclass.getName());
    if (interfaces != null) {
      tuple.addAll(ContainerUtil.map(interfaces, Class::getName));
    }
    return tuple;
  }

  protected ClassLoader getDefaultClassLoader() {
    int maxIndex = -1;
    ClassLoader bestLoader = null;
    ClassLoader nonPluginLoader = null;
    if (interfaces != null && interfaces.length > 0) {
      for (final Class anInterface : interfaces) {
        final ClassLoader loader = anInterface.getClassLoader();
        if (loader instanceof PluginClassLoader) {
          final int order = PluginManagerCore.getPluginLoadingOrder(((PluginClassLoader)loader).getPluginId());
          if (maxIndex < order) {
            maxIndex = order;
            bestLoader = loader;
          }
        }
        else if (nonPluginLoader == null) {
          nonPluginLoader = loader;
        }
      }
    }
    ClassLoader superLoader = null;
    if (superclass != null) {
      superLoader = superclass.getClassLoader();
      if (superLoader instanceof PluginClassLoader &&
          maxIndex < PluginManagerCore.getPluginLoadingOrder(((PluginClassLoader)superLoader).getPluginId())) {
        return superLoader;
      }
    }
    if (bestLoader != null) return bestLoader;
    return superLoader == null ? nonPluginLoader : superLoader;
  }

  private static Signature rename(Signature sig, int index) {
    return new Signature("CGLIB$" + sig.getName() + "$" + index,
                         sig.getDescriptor());
  }

  private static void getMethods(Class superclass, Class[] interfaces, List<Method> methods, List<Method> interfaceMethods, Set forcePublic)
  {
    ReflectUtils.addAllMethods(superclass, methods);
    List<Method> target = (interfaceMethods != null) ? interfaceMethods : methods;
    if (interfaces != null) {
      for (Class anInterface : interfaces) {
        if (anInterface != Factory.class) {
          ReflectUtils.addAllMethods(anInterface, target);
        }
      }
    }
    if (interfaceMethods != null) {
      if (forcePublic != null) {
        forcePublic.addAll(MethodWrapper.createSet(interfaceMethods));
      }
      methods.addAll(interfaceMethods);
    }
    CollectionUtils.filter(methods, new DuplicatesPredicate());
    CollectionUtils.filter(methods, new RejectModifierPredicate(Constants.ACC_STATIC | Constants.ACC_FINAL));
    CollectionUtils.filter(methods, new VisibilityPredicate(superclass, true));
  }

  public void generateClass($ClassVisitor v) throws Exception {
    Class sc = (superclass == null) ? Object.class : superclass;

    if (TypeUtils.isFinal(sc.getModifiers())) {
      throw new IllegalArgumentException("Cannot subclass final class " + sc);
    }
    List<Constructor> constructors = new ArrayList<>(Arrays.asList(sc.getDeclaredConstructors()));
    filterConstructors(sc, constructors);

    // Order is very important: must add superclass, then
    // its superclass chain, then each interface and
    // its superinterfaces.
    final Set forcePublic = new HashSet();
    List<Method> actualMethods = new ArrayList<>();
    final Map<Method, Method> covariantMethods = new HashMap<>();
    getMethods(sc, interfaces, actualMethods, new ArrayList<>(), forcePublic);

    //Changes by Peter Gromov & Gregory Shrago

    for(Class aClass = sc; aClass != null; aClass = aClass.getSuperclass()) {
      for (final Method method : aClass.getDeclaredMethods()) {
        if (actualMethods.contains(method)) {
          removeAllCovariantMethods(actualMethods, method, covariantMethods);
        }
      }
    }


    ClassEmitter e = new ClassEmitter(v);
    e.begin_class(Constants.V1_2,
                  Constants.ACC_PUBLIC,
                  getClassName(),
                  $Type.getType(sc),
                  (useFactory ?
                   TypeUtils.add(TypeUtils.getTypes(interfaces), FACTORY) :
                   TypeUtils.getTypes(interfaces)),
                  Constants.SOURCE_FILE);
    List constructorInfo = CollectionUtils.transform(constructors, MethodInfoTransformer.getInstance());

    e.declare_field(Constants.ACC_PRIVATE, BOUND_FIELD, $Type.BOOLEAN_TYPE, null);
    if (!interceptDuringConstruction) {
      e.declare_field(Constants.ACC_PRIVATE, CONSTRUCTED_FIELD, $Type.BOOLEAN_TYPE, null);
    }
    e.declare_field(Constants.PRIVATE_FINAL_STATIC, THREAD_CALLBACKS_FIELD, THREAD_LOCAL, null);
    e.declare_field(Constants.PRIVATE_FINAL_STATIC, STATIC_CALLBACKS_FIELD, CALLBACK_ARRAY, null);

    for (int i = 0; i < callbackTypes.length; i++) {
      e.declare_field(Constants.ACC_PRIVATE, getCallbackField(i), callbackTypes[i], null);
    }
    final Map<Method, MethodInfo> methodInfoMap = new HashMap<>();
    for (Method method : actualMethods) {
      if (isJdk8DefaultMethod(method)) {
        continue;
      }
      int modifiers =
        Constants.ACC_FINAL | (method.getModifiers() & ~Constants.ACC_ABSTRACT & ~Constants.ACC_NATIVE & ~Constants.ACC_SYNCHRONIZED);
      if (forcePublic.contains(MethodWrapper.create(method))) {
        modifiers = (modifiers & ~Constants.ACC_PROTECTED) | Constants.ACC_PUBLIC;
      }
      if (covariantMethods.containsKey(method)) {
        modifiers = modifiers | Constants.ACC_BRIDGE;
      }
      methodInfoMap.put(method, ReflectUtils.getMethodInfo(method, modifiers));
    }

    emitMethods(e, methodInfoMap, covariantMethods);
    emitConstructors(e, constructorInfo);
    emitSetThreadCallbacks(e);
    emitSetStaticCallbacks(e);
    emitBindCallbacks(e);

    if (useFactory) {
      int[] keys = getCallbackKeys();
      emitNewInstanceCallbacks(e);
      emitNewInstanceCallback(e);
      emitNewInstanceMultiarg(e, constructorInfo);
      emitGetCallback(e, keys);
      emitSetCallback(e, keys);
      emitGetCallbacks(e);
      emitSetCallbacks(e);
    }

    e.end_class();
  }

  private static boolean isJdk8DefaultMethod(Method method) {
    return ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) ==
         Modifier.PUBLIC) && method.getDeclaringClass().isInterface();
  }

  private static void removeAllCovariantMethods(final List<Method> actualMethods, final Method method, final Map<Method, Method> covariantMethods) {
    if ((method.getModifiers() & Constants.ACC_SYNTHETIC) != 0) {
      return;
    }

    for (Iterator<Method> it = actualMethods.iterator(); it.hasNext();) {
      Method actualMethod = it.next();
      if (actualMethod.equals(method)) {
        continue;
      }

      if (!actualMethod.getName().equals(method.getName()) ||
          !Arrays.equals(actualMethod.getParameterTypes(), method.getParameterTypes())) {
        continue;
      }

      if (ReflectionUtil.isAssignable(actualMethod.getReturnType(), method.getReturnType())) {
        if ((actualMethod.getModifiers() & Constants.ACC_ABSTRACT) != 0 || (actualMethod.getModifiers() & Constants.ACC_SYNTHETIC) != 0) {
          covariantMethods.put(actualMethod, method); //generate bridge
        }
        else {
          it.remove();
        }
      }
    }
  }

  /**
   * Filter the list of constructors from the superclass. The
   * constructors which remain will be included in the generated
   * class. The default implementation is to filter out all private
   * constructors, but subclasses may extend Enhancer to override this
   * behavior.
   * @param sc the superclass
   * @param constructors the list of all declared constructors from the superclass
   * @throws IllegalArgumentException if there are no non-private constructors
   */
  protected void filterConstructors(Class sc, List<Constructor> constructors) {
    CollectionUtils.filter(constructors, new VisibilityPredicate(sc, true));
    if (constructors.size() == 0) {
      throw new IllegalArgumentException("No visible constructors in " + sc);
    }
  }

  protected Object firstInstance(Class type) throws Exception {
    if (classOnly) {
      return type;
    } else {
      return createUsingReflection(type);
    }
  }

  protected Object nextInstance(Object instance) {
    Class protoclass = (instance instanceof Class) ? (Class) instance : instance.getClass();
    if (classOnly) {
      return protoclass;
    } else if (instance instanceof Factory) {
      if (argumentTypes != null) {
        return ((Factory)instance).newInstance(argumentTypes, arguments, callbacks);
      } else {
        return ((Factory)instance).newInstance(callbacks);
      }
    } else {
      return createUsingReflection(protoclass);
    }
  }

  private static void setThreadCallbacks(Class type, Callback[] callbacks) {
    setCallbacksHelper(type, callbacks, SET_THREAD_CALLBACKS_NAME);
  }

  private static void setCallbacksHelper(Class type, Callback[] callbacks, String methodName) {
    // TODO: optimize
    try {
      Method setter = getCallbacksSetter(type, methodName);
      setter.invoke(null, (Object)callbacks);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(type + " is not an enhanced class");
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new CodeGenerationException(e);
    }
  }

  private static Method getCallbacksSetter(Class type, String methodName) throws NoSuchMethodException {
    return type.getDeclaredMethod(methodName, Callback[].class);
  }

  private Object createUsingReflection(Class type) {
    setThreadCallbacks(type, callbacks);
    try{

      if (argumentTypes != null) {

        return ReflectUtils.newInstance(type, argumentTypes, arguments);

      } else {

        return ReflectUtils.newInstance(type);

      }
    }finally{
      // clear thread callbacks to allow them to be gc'd
      setThreadCallbacks(type, null);
    }
  }

  private void emitConstructors(ClassEmitter ce, List constructors) {
    boolean seenNull = false;
    for (final Object constructor1 : constructors) {
      MethodInfo constructor = (MethodInfo)constructor1;
      CodeEmitter e = EmitUtils.begin_method(ce, constructor, Constants.ACC_PUBLIC);
      e.load_this();
      e.dup();
      e.load_args();
      Signature sig = constructor.getSignature();
      seenNull = seenNull || sig.getDescriptor().equals("()V");
      e.super_invoke_constructor(sig);
      e.invoke_static_this(BIND_CALLBACKS);
      if (!interceptDuringConstruction) {
        e.load_this();
        e.push(1);
        e.putfield(CONSTRUCTED_FIELD);
      }
      e.return_value();
      e.end_method();
    }
    if (!classOnly && !seenNull && arguments == null) {
      throw new IllegalArgumentException("Superclass has no null constructors but no arguments were given");
    }
  }

  private int[] getCallbackKeys() {
    int[] keys = new int[callbackTypes.length];
    for (int i = 0; i < callbackTypes.length; i++) {
      keys[i] = i;
    }
    return keys;
  }

  private static void emitGetCallback(ClassEmitter ce, int[] keys) {
    final CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, GET_CALLBACK, null);
    e.load_this();
    e.invoke_static_this(BIND_CALLBACKS);
    e.load_this();
    e.load_arg(0);
    e.process_switch(keys, new ProcessSwitchCallback() {
      public void processCase(int key, $Label end) {
        e.getfield(getCallbackField(key));
        e.goTo(end);
      }
      public void processDefault() {
        e.pop(); // stack height
        e.aconst_null();
      }
    });
    e.return_value();
    e.end_method();
  }

  private void emitSetCallback(ClassEmitter ce, int[] keys) {
    final CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, SET_CALLBACK, null);
    e.load_this();
    e.load_arg(1);
    e.load_arg(0);
    e.process_switch(keys, new ProcessSwitchCallback() {
      public void processCase(int key, $Label end) {
        e.checkcast(callbackTypes[key]);
        e.putfield(getCallbackField(key));
        e.goTo(end);
      }
      public void processDefault() {
        final $Type type = $Type.getType(AssertionError.class);
        e.new_instance(type);
        e.dup();
        e.invoke_constructor(type);
        e.athrow();
      }
    });
    e.return_value();
    e.end_method();
  }

  private void emitSetCallbacks(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, SET_CALLBACKS, null);
    e.load_this();
    e.load_arg(0);
    for (int i = 0; i < callbackTypes.length; i++) {
      e.dup2();
      e.aaload(i);
      e.checkcast(callbackTypes[i]);
      e.putfield(getCallbackField(i));
    }
    e.return_value();
    e.end_method();
  }

  private void emitGetCallbacks(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, GET_CALLBACKS, null);
    e.load_this();
    e.invoke_static_this(BIND_CALLBACKS);
    e.load_this();
    e.push(callbackTypes.length);
    e.newarray(CALLBACK);
    for (int i = 0; i < callbackTypes.length; i++) {
      e.dup();
      e.push(i);
      e.load_this();
      e.getfield(getCallbackField(i));
      e.aastore();
    }
    e.return_value();
    e.end_method();
  }

  private static void emitNewInstanceCallbacks(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, NEW_INSTANCE, null);
    e.load_arg(0);
    e.invoke_static_this(SET_THREAD_CALLBACKS);
    emitCommonNewInstance(e);
  }

  private static void emitCommonNewInstance(CodeEmitter e) {
    e.new_instance_this();
    e.dup();
    e.invoke_constructor_this();
    e.aconst_null();
    e.invoke_static_this(SET_THREAD_CALLBACKS);
    e.return_value();
    e.end_method();
  }

  private void emitNewInstanceCallback(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, SINGLE_NEW_INSTANCE, null);
    switch (callbackTypes.length) {
      case 0:
        // TODO: make sure Callback is null
        break;
      case 1:
        // for now just make a new array; TODO: optimize
        e.push(1);
        e.newarray(CALLBACK);
        e.dup();
        e.push(0);
        e.load_arg(0);
        e.aastore();
        e.invoke_static_this(SET_THREAD_CALLBACKS);
        break;
      default:
        e.throw_exception(ILLEGAL_STATE_EXCEPTION, "More than one callback object required");
    }
    emitCommonNewInstance(e);
  }

  private static void emitNewInstanceMultiarg(ClassEmitter ce, List constructors) {
    final CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, MULTIARG_NEW_INSTANCE, null);
    e.load_arg(2);
    e.invoke_static_this(SET_THREAD_CALLBACKS);
    e.new_instance_this();
    e.dup();
    e.load_arg(0);
    EmitUtils.constructor_switch(e, constructors, new ObjectSwitchCallback() {
      public void processCase(Object key, $Label end) {
        MethodInfo constructor = (MethodInfo)key;
        $Type[] types = constructor.getSignature().getArgumentTypes();
        for (int i = 0; i < types.length; i++) {
          e.load_arg(1);
          e.push(i);
          e.aaload();
          e.unbox(types[i]);
        }
        e.invoke_constructor_this(constructor.getSignature());
        e.goTo(end);
      }
      public void processDefault() {
        e.throw_exception(ILLEGAL_ARGUMENT_EXCEPTION, "Constructor not found");
      }
    });
    e.aconst_null();
    e.invoke_static_this(SET_THREAD_CALLBACKS);
    e.return_value();
    e.end_method();
  }

  private void emitMethods(final ClassEmitter ce, Map<Method, MethodInfo> methodMap, final Map<Method, Method> covariantMethods) {
    CallbackGenerator[] generators = CallbackInfo.getGenerators(callbackTypes);
    Map<MethodInfo, MethodInfo> covariantInfoMap = new HashMap<>();
    for (Method method : methodMap.keySet()) {
      final Method delegate = covariantMethods.get(method);
      if (delegate != null) {
        covariantInfoMap.put(methodMap.get(method), ReflectUtils.getMethodInfo(delegate, delegate.getModifiers()));
      }
    }
    BridgeMethodGenerator bridgeMethodGenerator = new BridgeMethodGenerator(covariantInfoMap);

    Map<CallbackGenerator,List<MethodInfo>> groups = new HashMap<>();
    final Map<MethodInfo,Integer> indexes = new HashMap<>();
    final Map<MethodInfo,Integer> originalModifiers = new HashMap<>();
    final Map positions = CollectionUtils.getIndexMap(new ArrayList<>(methodMap.values()));

    for (Method actualMethod : methodMap.keySet()) {
      MethodInfo method = methodMap.get(actualMethod);
      int index = filter.accept(actualMethod);
      if (index >= callbackTypes.length) {
        throw new IllegalArgumentException("Callback filter returned an index that is too large: " + index);
      }
      originalModifiers.put(method, (actualMethod != null) ? actualMethod.getModifiers() : method.getModifiers());
      indexes.put(method, index);
      final CallbackGenerator generator = covariantMethods.containsKey(actualMethod)? bridgeMethodGenerator : generators[index];
      List<MethodInfo> group = groups.get(generator);
      if (group == null) {
        groups.put(generator, group = new ArrayList<>(methodMap.size()));
      }
      group.add(method);
    }

    CodeEmitter se = ce.getStaticHook();
    se.new_instance(THREAD_LOCAL);
    se.dup();
    se.invoke_constructor(THREAD_LOCAL, CSTRUCT_NULL);
    se.putfield(THREAD_CALLBACKS_FIELD);

    CallbackGenerator.Context context = new CallbackGenerator.Context() {
      public ClassLoader getClassLoader() {
  	return AdvancedEnhancer.this.getClassLoader();
      }
      public int getOriginalModifiers(MethodInfo method) {
        return originalModifiers.get(method);
      }
      public int getIndex(MethodInfo method) {
        return indexes.get(method);
      }
      public void emitCallback(CodeEmitter e, int index) {
        emitCurrentCallback(e, index);
      }
      public Signature getImplSignature(MethodInfo method) {
        return rename(method.getSignature(), (Integer)positions.get(method));
      }

      @Override
      public void emitInvoke(CodeEmitter codeEmitter, MethodInfo methodInfo) {
        codeEmitter.super_invoke(methodInfo.getSignature());
      }

      public CodeEmitter beginMethod(ClassEmitter ce, MethodInfo method) {
        CodeEmitter e = EmitUtils.begin_method(ce, method);
        if (!interceptDuringConstruction &&
            !TypeUtils.isAbstract(method.getModifiers())) {
          $Label constructed = e.make_label();
          e.load_this();
          e.getfield(CONSTRUCTED_FIELD);
          e.if_jump(e.NE, constructed);
          e.load_this();
          e.load_args();
          e.super_invoke();
          e.return_value();
          e.mark(constructed);
        }
        return e;
      }
    };
    Set<CallbackGenerator> seenGen = new HashSet<>();
    for (int i = 0; i < callbackTypes.length + 1; i++) {
      CallbackGenerator gen = i == callbackTypes.length? bridgeMethodGenerator : generators[i];
      if (!seenGen.contains(gen)) {
        seenGen.add(gen);
        final List<MethodInfo> fmethods = groups.get(gen);
        if (fmethods != null) {
          try {
            gen.generate(ce, context, fmethods);
            gen.generateStatic(se, context, fmethods);
          } catch (RuntimeException x) {
            throw x;
          } catch (Exception x) {
            throw new CodeGenerationException(x);
          }
        }
      }
    }
    se.return_value();
    se.end_method();
  }

  private static void emitSetThreadCallbacks(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC | Constants.ACC_STATIC,
                                    SET_THREAD_CALLBACKS,
                                    null);
    e.getfield(THREAD_CALLBACKS_FIELD);
    e.load_arg(0);
    e.invoke_virtual(THREAD_LOCAL, THREAD_LOCAL_SET);
    e.return_value();
    e.end_method();
  }

  private static void emitSetStaticCallbacks(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC | Constants.ACC_STATIC,
                                    SET_STATIC_CALLBACKS,
                                    null);
    e.load_arg(0);
    e.putfield(STATIC_CALLBACKS_FIELD);
    e.return_value();
    e.end_method();
  }

  private static void emitCurrentCallback(CodeEmitter e, int index) {
    e.load_this();
    e.getfield(getCallbackField(index));
    e.dup();
    $Label end = e.make_label();
    e.ifnonnull(end);
    e.pop(); // stack height
    e.load_this();
    e.invoke_static_this(BIND_CALLBACKS);
    e.load_this();
    e.getfield(getCallbackField(index));
    e.mark(end);
  }

  private void emitBindCallbacks(ClassEmitter ce) {
    CodeEmitter e = ce.begin_method(Constants.PRIVATE_FINAL_STATIC,
                                    BIND_CALLBACKS,
                                    null);
    Local me = e.make_local();
    e.load_arg(0);
    e.checkcast_this();
    e.store_local(me);

    $Label end = e.make_label();
    e.load_local(me);
    e.getfield(BOUND_FIELD);
    e.if_jump(e.NE, end);
    e.load_local(me);
    e.push(1);
    e.putfield(BOUND_FIELD);

    e.getfield(THREAD_CALLBACKS_FIELD);
    e.invoke_virtual(THREAD_LOCAL, THREAD_LOCAL_GET);
    e.dup();
    $Label found_callback = e.make_label();
    e.ifnonnull(found_callback);
    e.pop();

    e.getfield(STATIC_CALLBACKS_FIELD);
    e.dup();
    e.ifnonnull(found_callback);
    e.pop();
    e.goTo(end);

    e.mark(found_callback);
    e.checkcast(CALLBACK_ARRAY);
    e.load_local(me);
    e.swap();
    for (int i = callbackTypes.length - 1; i >= 0; i--) {
      if (i != 0) {
        e.dup2();
      }
      e.aaload(i);
      e.checkcast(callbackTypes[i]);
      e.putfield(getCallbackField(i));
    }

    e.mark(end);
    e.return_value();
    e.end_method();
  }

  private static String getCallbackField(int index) {
    return "CGLIB$CALLBACK_" + index;
  }
}
