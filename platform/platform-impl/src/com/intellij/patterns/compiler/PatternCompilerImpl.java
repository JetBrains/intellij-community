/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.patterns.compiler;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.ElementPatternCondition;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class PatternCompilerImpl<T> implements PatternCompiler<T> {

  private static final Logger LOG = Logger.getInstance(PatternCompilerImpl.class.getName());

  private final Set<Method> myStaticMethods;
  private final StringInterner myStringInterner = new StringInterner();

  public PatternCompilerImpl(final List<Class> patternClasses) {
    myStaticMethods = getStaticMethods(patternClasses);
  }

  private static final Node ERROR_NODE = new Node(null, null, null);
  @Override
  public ElementPattern<T> createElementPattern(final String text, final String displayName) {
    try {
      return compileElementPattern(text);
    }
    catch (Exception ex) {
      final Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
      LOG.warn("error processing place: " + displayName + " [" + text + "]", cause);
      return new LazyPresentablePattern<>(new Node(ERROR_NODE, text, null), Collections.emptySet());
    }
  }

  //@Override
  //public ElementPattern<T> compileElementPattern(final String text) {
  //  return processElementPatternText(text, new Function<Frame, Object>() {
  //    public Object fun(final Frame frame) {
  //      try {
  //        final Object[] args = frame.params.toArray();
  //        preInvoke(frame.target, frame.methodName, args);
  //        return invokeMethod(frame.target, frame.methodName, args, myStaticMethods);
  //      }
  //      catch (Throwable throwable) {
  //        throw new IllegalArgumentException(text, throwable);
  //      }
  //    }
  //  });
  //}

  @Override
  public synchronized ElementPattern<T> compileElementPattern(final String text) {
    Node node = processElementPatternText(text, frame -> {
      final Object[] args = frame.params.toArray();
      for (int i = 0, argsLength = args.length; i < argsLength; i++) {
        args[i] = args[i] instanceof String ? myStringInterner.intern((String)args[i]) : args[i];
      }
      return new Node((Node)frame.target, myStringInterner.intern(frame.methodName), args.length == 0 ? ArrayUtil.EMPTY_OBJECT_ARRAY : args);
    });
    if (node == null) node = new Node(ERROR_NODE, text, null);
    return new LazyPresentablePattern<>(node, myStaticMethods);
  }

  private static Set<Method> getStaticMethods(List<Class> patternClasses) {
    return new THashSet<>(ContainerUtil.concat(
      patternClasses,
      aClass -> ContainerUtil.findAll(aClass.getMethods(),
                              method -> Modifier.isStatic(method.getModifiers()) &&
                                        Modifier.isPublic(method.getModifiers()) &&
                                        !Modifier.isAbstract(method.getModifiers()) &&
                                        ElementPattern.class.isAssignableFrom(method.getReturnType()))));
  }

  private enum State {
    init, name, name_end,
    param_start, param_end, literal, escape,
    invoke, invoke_end
  }

  private static class Frame {
    State state = State.init;
    Object target;
    String methodName;
    ArrayList<Object> params = new ArrayList<>();
  }

  @Nullable
  private static <T> T processElementPatternText(final String text, final Function<Frame, Object> executor) {
    final Stack<Frame> stack = new Stack<>();
    int curPos = 0;
    Frame curFrame = new Frame();
    Object curResult = null;
    final StringBuilder curString = new StringBuilder();
    while (true) {
      if (curPos > text.length()) break;
      final char ch = curPos++ < text.length()? text.charAt(curPos-1) : 0;
      switch (curFrame.state) {
        case init:
          if (Character.isWhitespace(ch)) {
          }
          else if (Character.isJavaIdentifierStart(ch)) {
            curString.append(ch);
            curFrame.state = State.name;
          }
          else {
            throwError(curPos, ch, "method call expected");
          }
          break;
        case name:
          if (Character.isJavaIdentifierPart(ch)) {
            curString.append(ch);
          }
          else if (ch == '(' || Character.isWhitespace(ch)) {
            curFrame.methodName = curString.toString();
            curString.setLength(0);
            curFrame.state = ch == '('? State.param_start : State.name_end;
          }
          else {
            throwError(curPos, ch, "'"+curString+ch+"' method name start is invalid, '(' expected");
          }
          break;
        case name_end:
          if (ch == '(') {
            curFrame.state = State.param_start;
          }
          else if (!Character.isWhitespace(ch)) {
            throwError(curPos, ch, "'(' expected after '"+curFrame.methodName+"'");
          }
          break;
        case param_start:
          if (Character.isWhitespace(ch)) {
          }
          else if (Character.isDigit(ch) || ch == '-' || ch == '\"') {
            curFrame.state = State.literal;
            curString.append(ch);
          }
          else if (ch == ')') {
            curFrame.state = State.invoke;
          }
          else if (Character.isJavaIdentifierStart(ch)) {
            curString.append(ch);
            stack.push(curFrame);
            curFrame = new Frame();
            curFrame.state = State.name;
          }
          else {
            throwError(curPos, ch, "expression expected in '" + curFrame.methodName + "' call");
          }
          break;
        case param_end:
          if (ch == ')') {
            curFrame.state = State.invoke;
          }
          else if (ch == ',') {
            curFrame.state = State.param_start;
          }
          else if (!Character.isWhitespace(ch)) {
            throwError(curPos, ch, "')' or ',' expected in '" + curFrame.methodName + "' call");
          }
          break;
        case literal:
          if (curString.charAt(0) == '\"') {
            curString.append(ch);
            if (ch == '\\') {
              curFrame.state = State.escape;
            }
            else {
              if (ch == '\"') {
                curFrame.params.add(makeParam(curString.toString()));
                curString.setLength(0);
                curFrame.state = State.param_end;
              }
            }
          }
          else if (Character.isWhitespace(ch) || ch == ',' || ch == ')') {
            curFrame.params.add(makeParam(curString.toString()));
            curString.setLength(0);
            curFrame.state = ch == ')' ? State.invoke :
                             ch == ',' ? State.param_start : State.param_end;
          }
          else {
            curString.append(ch);
          }
          break;
        case escape:
          if (ch != 0) {
            curString.append(ch);
            curFrame.state = State.literal;
          }
          else {
            throwError(curPos, ch, "unclosed escape sequence");
          }
          break;
        case invoke:
          curResult = executor.fun(curFrame);
          if (ch == 0 && stack.isEmpty()) {
            //noinspection unchecked
            return (T)curResult;
          }
          else if (ch == '.') {
            curFrame = new Frame();
            curFrame.target = curResult;
            curFrame.state = State.init;
            curResult = null;
          }
          else if (ch == ',' || ch == ')') {
            curFrame = stack.pop();
            curFrame.params.add(curResult);
            curResult = null;
            curFrame.state = ch == ')' ? State.invoke : State.param_start;
          }
          else if (Character.isWhitespace(ch)) {
            curFrame.state = State.invoke_end;
          }
          else {
            throwError(curPos, ch, (stack.isEmpty()? "'.' or <eof>" : "'.' or ')'")
                                            + "expected after '" + curFrame.methodName + "' call");
          }
          break;
        case invoke_end:
          if (ch == 0 && stack.isEmpty()) {
            //noinspection unchecked
            return (T)curResult;
          }
          else if (ch == ')') {
            curFrame.state = State.invoke;
          }
          else if (ch == ',') {
            curFrame.state = State.param_start;
          }
          else if (ch == '.') {
            curFrame = new Frame();
            curFrame.target = curResult;
            curFrame.state = State.init;
            curResult = null;
          }
          else if (!Character.isWhitespace(ch)) {
            throwError(curPos, ch, (stack.isEmpty()? "'.' or <eof>" : "'.' or ')'")
                                            + "expected after '" + curFrame.methodName + "' call");
          }
          break;
      }
    }
    return null;
  }

  private static void throwError(int offset, char ch, String message) {
    throw new IllegalStateException(offset+"("+ch+"): "+message);
  }

  private static Object makeParam(final String s) {
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      return StringUtil.unescapeStringCharacters(s.substring(1, s.length() - 1));
    }
    try {
      return Integer.valueOf(s);
    }
    catch (NumberFormatException ignored) {}
    return s;
  }

  private static Class<?> getNonPrimitiveType(final Class<?> type) {
    if (!type.isPrimitive()) return type;
    if (type == boolean.class) return Boolean.class;
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private static Object invokeMethod(@Nullable final Object target, final String methodName, final Object[] arguments, final Collection<Method> staticMethods) throws Throwable {
    final Ref<Boolean> convertVarArgs = Ref.create(Boolean.FALSE);
    final Collection<Method> methods = target == null ? staticMethods : Arrays.asList(target.getClass().getMethods());
    final Method method = findMethod(methodName, arguments, methods, convertVarArgs);
    if (method != null) {
      try {
        final Object[] newArgs;
        if (!convertVarArgs.get()) newArgs = arguments;
        else {
          final Class<?>[] parameterTypes = method.getParameterTypes();
          newArgs = new Object[parameterTypes.length];
          System.arraycopy(arguments, 0, newArgs, 0, parameterTypes.length - 1);
          final Object[] varArgs = (Object[])Array
            .newInstance(parameterTypes[parameterTypes.length - 1].getComponentType(), arguments.length - parameterTypes.length + 1);
          System.arraycopy(arguments, parameterTypes.length - 1, varArgs, 0, varArgs.length);
          newArgs[parameterTypes.length - 1] = varArgs;
        }
        return method.invoke(target, newArgs);
      }
      catch (InvocationTargetException e) {
        throw e.getTargetException();
      }
    }
    throw new NoSuchMethodException("unknown symbol: " + methodName + "(" + StringUtil.join(arguments, o -> String.valueOf(o), ", ") + ")");
  }

  @Nullable
  private static Method findMethod(final String methodName, final Object[] arguments, final Collection<Method> methods, Ref<Boolean> convertVarArgs) {
    main: for (Method method : methods) {
      if (!methodName.equals(method.getName())) continue;
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (!method.isVarArgs() && parameterTypes.length != arguments.length) continue;
      convertVarArgs.set(false);
      for (int i = 0, parameterTypesLength = parameterTypes.length; i < arguments.length; i++) {
        final Class<?> type = getNonPrimitiveType(i < parameterTypesLength ? parameterTypes[i] : parameterTypes[parameterTypesLength - 1]);
        final Object argument = arguments[i];
        final Class<?> componentType =
          method.isVarArgs() && i < parameterTypesLength - 1 ? null : parameterTypes[parameterTypesLength - 1].getComponentType();
        if (argument != null) {
          if (!type.isInstance(argument)) {
            if ((componentType == null || !componentType.isInstance(argument))) continue main;
            else convertVarArgs.set(true);
          }
        }
      }
      if (parameterTypes.length > arguments.length) {
        convertVarArgs.set(true);
      }
      return method;
    }
    return null;
  }

  @Override
  public String dumpContextDeclarations() {
    final StringBuilder sb = new StringBuilder();
    final THashMap<Class, Collection<Class>> classes = new THashMap<>();
    final THashSet<Class> missingClasses = new THashSet<>();
    classes.put(Object.class, missingClasses);
    for (Method method : myStaticMethods) {
      for (Class<?> type = method.getReturnType(); type != null && ElementPattern.class.isAssignableFrom(type); type = type.getSuperclass()) {
        final Class<?> enclosingClass = type.getEnclosingClass();
        if (enclosingClass != null) {
          Collection<Class> list = classes.get(enclosingClass);
          if (list == null) {
            list = new THashSet<>();
            classes.put(enclosingClass, list);
          }
          list.add(type);
        }
        else if (!classes.containsKey(type)) {
          classes.put(type, null);
        }
      }
    }
    for (Class aClass : classes.keySet()) {
      if (aClass == Object.class) continue;
      printClass(aClass, classes, sb);
    }
    for (Method method : myStaticMethods) {
      printMethodDeclaration(method, sb, classes);
    }
    for (Class aClass : missingClasses) {
      sb.append("class ").append(aClass.getSimpleName());
      final Class superclass = aClass.getSuperclass();
      if (missingClasses.contains(superclass)) {
        sb.append(" extends ").append(superclass.getSimpleName());
      }
      sb.append("{}\n");
    }
    //System.out.println(sb);
    return sb.toString();
  }

  private static void printClass(Class aClass, Map<Class, Collection<Class>> classes, StringBuilder sb) {
    final boolean isInterface = aClass.isInterface();
    sb.append(isInterface ? "interface ": "class ");
    dumpType(aClass, aClass, sb, classes);
    final Type superClass = aClass.getGenericSuperclass();
    final Class rawSuperClass = (Class)(superClass instanceof ParameterizedType ? ((ParameterizedType)superClass).getRawType() : superClass);
    if (superClass != null && classes.containsKey(rawSuperClass)) {
      sb.append(" extends ");
      dumpType(null, superClass, sb, classes);
    }
    int implementsIdx = 1;
    for (Type superInterface : aClass.getGenericInterfaces()) {
      final Class rawSuperInterface = (Class)(superInterface instanceof ParameterizedType ? ((ParameterizedType)superInterface).getRawType() : superInterface);
      if (classes.containsKey(rawSuperInterface)) {
        if (implementsIdx++ == 1) sb.append(isInterface? " extends " : " implements ");
        else sb.append(", ");
        dumpType(null, superInterface, sb, classes);
      }
    }
    sb.append(" {\n");
    for (Method method : aClass.getDeclaredMethods()) {
      if (Modifier.isStatic(method.getModifiers()) ||
          !Modifier.isPublic(method.getModifiers()) ||
          Modifier.isVolatile(method.getModifiers())) continue;
      printMethodDeclaration(method, sb.append("  "), classes);
    }
    final Collection<Class> innerClasses = classes.get(aClass);
    sb.append("}\n");
    if (innerClasses != null) {
      for (Class innerClass : innerClasses) {
        printClass(innerClass, classes, sb);
      }
    }
  }

  private static void dumpType(GenericDeclaration owner, Type type, StringBuilder sb, Map<Class, Collection<Class>> classes) {
    if (type instanceof Class) {
      final Class aClass = (Class)type;
      final Class enclosingClass = aClass.getEnclosingClass();
      if (enclosingClass != null) {
        sb.append(enclosingClass.getSimpleName()).append("_");
      }
      else if (!aClass.isArray() && !aClass.isPrimitive() && !aClass.getName().startsWith("java.") && !classes.containsKey(aClass)) {
        classes.get(Object.class).add(aClass);
      }
      sb.append(aClass.getSimpleName());
      if (owner == aClass) {
        dumpTypeParametersArray(owner, aClass.getTypeParameters(), sb, "<", ">", classes);
      }
    }
    else if (type instanceof TypeVariable) {
      TypeVariable typeVariable = (TypeVariable)type;
      sb.append(typeVariable.getName());
      if (typeVariable.getGenericDeclaration() == owner) {
        dumpTypeParametersArray(null, typeVariable.getBounds(), sb, " extends ", "", classes);
      }
    }
    else if (type instanceof WildcardType) {
      final WildcardType wildcardType = (WildcardType)type;
      sb.append("?");
      dumpTypeParametersArray(owner, wildcardType.getUpperBounds(), sb, " extends ", "", classes);
      dumpTypeParametersArray(owner, wildcardType.getLowerBounds(), sb, " super ", "", classes);
    }
    else if (type instanceof ParameterizedType) {
      final ParameterizedType parameterizedType = (ParameterizedType)type;
      final Type raw = parameterizedType.getRawType();
      dumpType(null, raw, sb, classes);
      dumpTypeParametersArray(owner, parameterizedType.getActualTypeArguments(), sb, "<", ">", classes);
    }
    else if (type instanceof GenericArrayType) {
      dumpType(owner, ((GenericArrayType)type).getGenericComponentType(), sb, classes);
      sb.append("[]");
    }
  }

  private static void dumpTypeParametersArray(GenericDeclaration owner, final Type[] typeVariables,
                                              final StringBuilder sb,
                                              final String prefix, final String suffix, Map<Class, Collection<Class>> classes) {
    int typeVarIdx = 1;
    for (Type typeVariable : typeVariables) {
      if (typeVariable == Object.class) continue;
      if (typeVarIdx++ == 1) sb.append(prefix);
      else sb.append(", ");
      dumpType(owner, typeVariable, sb, classes);
    }
    if (typeVarIdx > 1) sb.append(suffix);
  }

  private static void printMethodDeclaration(Method method, StringBuilder sb, Map<Class, Collection<Class>> classes) {
    if (Modifier.isStatic(method.getModifiers())) {
      sb.append("static ");
    }
    dumpTypeParametersArray(method, method.getTypeParameters(), sb, "<", "> ", classes);
    dumpType(null, method.getGenericReturnType(), sb, classes);
    sb.append(" ").append(method.getName()).append("(");
    int paramIdx = 1;
    for (Type parameter : method.getGenericParameterTypes()) {
      if (paramIdx != 1) sb.append(", ");
      dumpType(null, parameter, sb, classes);
      sb.append(" ").append("p").append(paramIdx++);
    }
    sb.append(")");
    if (!method.getDeclaringClass().isInterface()) sb.append("{}");
    sb.append("\n");
  }

  //@Nullable
  //public static ElementPattern<PsiElement> createElementPatternGroovy(final String text, final String displayName, final String supportId) {
  //  final Binding binding = new Binding();
  //  final ArrayList<MetaMethod> metaMethods = new ArrayList<MetaMethod>();
  //  for (Class aClass : getPatternClasses(supportId)) {
  //    // walk super classes as well?
  //    for (CachedMethod method : ReflectionCache.getCachedClass(aClass).getMethods()) {
  //      if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())) continue;
  //      metaMethods.add(method);
  //    }
  //  }
  //
  //  final ExpandoMetaClass metaClass = new ExpandoMetaClass(Object.class, false, metaMethods.toArray(new MetaMethod[metaMethods.size()]));
  //  final GroovyShell shell = new GroovyShell(binding);
  //  try {
  //    final Script script = shell.parse("return " + text);
  //    metaClass.initialize();
  //    script.setMetaClass(metaClass);
  //    final Object value = script.run();
  //    return value instanceof ElementPattern ? (ElementPattern<PsiElement>)value : null;
  //  }
  //  catch (GroovyRuntimeException ex) {
  //    Configuration.LOG.warn("error processing place: "+displayName+" ["+text+"]", ex);
  //  }
  //  return null;
  //}

  private static final ElementPattern<?> ALWAYS_FALSE = new FalsePattern();

  private static class Node {
    final Node target;
    final String method;
    final Object[] args;

    Node(@Nullable Node target, @Nullable String method, @Nullable Object[] args) {
      this.target = target;
      this.method = method;
      this.args = args;
    }
  }

  private static class FalsePattern extends InitialPatternCondition<Object> implements ElementPattern<Object> {
    private final ElementPatternCondition<Object> myCondition = new ElementPatternCondition<>(this);

    protected FalsePattern() {
      super(Object.class);
    }

    @Override
    public boolean accepts(@Nullable final Object o) {
      return false;
    }

    @Override
    public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
      return false;
    }

    @Override
    public ElementPatternCondition<Object> getCondition() {
      return myCondition;
    }
  }


  public static class LazyPresentablePattern<T> implements ElementPattern<T> {

    private final Node myNode;
    private final Set<Method> myStaticMethods;
    private final long myHashCode;

    private ElementPattern<T> myCompiledPattern;

    public LazyPresentablePattern(@NotNull Node node, @NotNull Set<Method> staticMethods) {
      myNode = node;
      myStaticMethods = staticMethods;
      myHashCode = StringHash.calc(toString());
    }

    @Override
    public boolean accepts(@Nullable final Object o) {
      return getCompiledPattern().accepts(o, new ProcessingContext());
    }

    @Override
    public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
      return getCompiledPattern().accepts(o, context);
    }

    @Override
    public ElementPatternCondition<T> getCondition() {
      return getCompiledPattern().getCondition();
    }

    public ElementPattern<T> getCompiledPattern() {
      if (myCompiledPattern == null) {
        ElementPattern<?> result;
        try {
          result = compile();
        }
        catch (Throwable throwable) {
          LOG.warn(toString(), throwable);
          result = ALWAYS_FALSE;
        }
        //noinspection unchecked
        myCompiledPattern = (ElementPattern<T>)result;
      }
      return myCompiledPattern;
    }

    public ElementPattern<?> compile() throws Throwable {
      return myNode.target == ERROR_NODE ? ALWAYS_FALSE : (ElementPattern<?>)execute(myNode);
    }

    @Override
    public String toString() {
      if (myNode.target == ERROR_NODE && myNode.args == null) {
        return myNode.method;
      }
      StringBuilder sb = new StringBuilder();
      appendNode(myNode, sb);
      return sb.toString();
    }

    private static void appendNode(Node node, StringBuilder sb) {
      if (node.target == ERROR_NODE) {
        sb.append(node.method);
        return;
      }
      else if (node.target != null) {
        appendNode(node.target, sb);
        sb.append('.');
      }
      sb.append(node.method).append('(');
      boolean first = true;
      for (Object arg : (node.args == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : node.args)) {
        if (first) first = false;
        else sb.append(',').append(' ');
        if (arg instanceof Node) {
          appendNode((Node)arg, sb);
        }
        else if (arg instanceof String) {
          sb.append('\"').append(StringUtil.escapeStringCharacters((String)arg)).append('\"');
        }
        else if (arg instanceof Number) {
          sb.append(arg);
        }
      }
      sb.append(')');
    }

    private Object execute(final Node node) throws Throwable {
      final Object target = node.target != null? execute(node.target) : null;
      final String methodName = node.method;
      final Object[] args;
      if (node.args.length == 0) {
        args = node.args;
      }
      else {
        args = new Object[node.args.length];
        for (int i = 0, len = node.args.length; i < len; i++) {
          args[i] = node.args[i] instanceof Node? execute((Node)node.args[i]) : node.args[i];
        }
      }
      return invokeMethod(target, methodName, args, myStaticMethods);
    }

    @Override
    public int hashCode() {
      return (int)myHashCode;
    }

    @Override
    public boolean equals(final Object obj) {
      return obj instanceof LazyPresentablePattern &&
             ((LazyPresentablePattern)obj).myHashCode == myHashCode;
    }
  }
}
