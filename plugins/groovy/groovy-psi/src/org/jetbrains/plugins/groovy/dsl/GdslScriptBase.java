package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiClassPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.PsiModifierListOwnerPattern;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import groovy.lang.Closure;
import groovy.lang.Script;
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.Context;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes", "unused", "UnusedReturnValue"})
public abstract class GdslScriptBase extends Script {

  private static final String ideaVersion;
  static {
    String major = ApplicationInfo.getInstance().getMajorVersion();
    String minor = ApplicationInfo.getInstance().getMinorVersion();
    ideaVersion = major + (minor != null ? ("." + minor) : "");
  }
  public static String getIdeaVersion() {
    return ideaVersion;
  }

  private final List<Pair<ContextFilter, Closure>> enhancers = new ArrayList<>();
  public final List<Pair<ContextFilter, Closure>> getEnhancers() {
    return enhancers;
  }

  private final MultiMap staticInfo = new MultiMap();
  public final MultiMap getStaticInfo() {
    return staticInfo;
  }

  private boolean locked = false;

  public abstract void scriptBody();

  @Override
  public Boolean run() {
    try {
      scriptBody();
    }
    catch (InvalidVersionException ignore) {
      enhancers.clear();
    }

    return locked = true;
  }

  @SuppressWarnings("unused")
  public DslPointcut methodMissing(String name, Object args) { return DslPointcut.UNKNOWN; }

  public void contribute(Object cts, Closure toDo) {
    cts = handleImplicitBind(cts);

    if (cts instanceof DslPointcut) {
      if (((DslPointcut)cts).operatesOn(GroovyClassDescriptor.class)) {
        PointcutContextFilter filter = new PointcutContextFilter((DslPointcut<? super GroovyClassDescriptor, ?>)cts);
        addClassEnhancer(List.of(filter), toDo);
      }
      else {
        Logger.getInstance(getClass()).error("A non top-level pointcut passed to contributor");
      }

      return;
    }

    if (cts instanceof Map) {
      cts = new Context((Map)cts);
    }

    if (!(cts instanceof List)) {
      assert cts instanceof Context : "The contributor() argument must be a context";
      cts = List.of(cts);
    }

    List<Context> contexts = (List<Context>)ContainerUtil.filter(((List<?>)cts), x -> x != null);
    if (!contexts.isEmpty()) {
      List<ContextFilter> filters = ContainerUtil.map(contexts, x -> x.getFilter());
      addClassEnhancer(filters, toDo);
    }
  }

  public void contributor(Object cts, Closure toDo) { contribute(cts, toDo); }

  public void assertVersion(Object ver) { if (!supportsVersion(ver)) throw new InvalidVersionException(); }

  public void scriptSuperClass(Map args) { staticInfo.putValue("scriptSuperClass", args); }

  public boolean supportsVersion(Object ver) {
    if (ver instanceof String) {
      return StringUtil.compareVersionNumbers(ideaVersion, (String)ver) >= 0;
    }
    else if (ver instanceof Map) {
      String dsl = (String)((Map<?, ?>)ver).get("dsl");
      if (dsl != null && !dsl.isEmpty()) {
        return StringUtil.compareVersionNumbers("1.0", dsl) >= 0;
      }

      String intellij = (String)((Map<?, ?>)ver).get("intellij");
      if (intellij != null && !intellij.isEmpty()) {
        return StringUtil.compareVersionNumbers(ideaVersion, intellij) >= 0;
      }
    }

    return false;
  }

  public void addClassEnhancer(List<? extends ContextFilter> cts, Closure toDo) {
    assert !locked : "Contributing to GDSL is only allowed at the top-level of the *.gdsl script";
    enhancers.add(Pair.create(CompositeContextFilter.compose(cts, false), toDo));
  }

  private static class InvalidVersionException extends RuntimeException {
  }

  /**
   * Auxiliary methods for context definition
   */
  public Scope closureScope(Map args) { return new ClosureScope(args); }

  public Scope scriptScope(Map args) { return new ScriptScope(args); }

  public Scope classScope(Map args) { return new ClassScope(args); }

  public Scope annotatedScope(Map args) { return new AnnotatedScope(args); }

  /**
   * Context definition
   */
  public Context context(Map args) { return new Context(args); }

  public PsiModifierListOwnerPattern.Capture<PsiModifierListOwner> hasAnnotation(String annoQName) {
    return PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName);
  }

  public PsiClassPattern hasField(ElementPattern fieldCondition) {
    return PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition));
  }

  public PsiClassPattern hasMethod(ElementPattern methodCondition) {
    return PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition));
  }

  public DslPointcut bind(final Object arg) {
    return DslPointcut.bind(arg);
  }

  public Object handleImplicitBind(Object arg) {
    if (arg instanceof Map &&
        ((Map)arg).size() == 1 &&
        ((Map)arg).keySet().iterator().next() instanceof String &&
        ((Map)arg).values().iterator().next() instanceof DslPointcut) {
      return DslPointcut.bind(arg);
    }

    return arg;
  }

  public DslPointcut<GdslType, GdslType> subType(final Object arg) {
    return DslPointcut.subType(handleImplicitBind(arg));
  }

  public DslPointcut<GroovyClassDescriptor, GdslType> currentType(final Object arg) {
    return DslPointcut.currentType(handleImplicitBind(arg));
  }

  public DslPointcut<GroovyClassDescriptor, GdslType> enclosingType(final Object arg) {
    return DslPointcut.enclosingType(handleImplicitBind(arg));
  }

  public DslPointcut<Object, String> name(final Object arg) {
    return DslPointcut.name(handleImplicitBind(arg));
  }

  public DslPointcut<GroovyClassDescriptor, GdslMethod> enclosingMethod(final Object arg) {
    return DslPointcut.enclosingMethod(handleImplicitBind(arg));
  }
}
