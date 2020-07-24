// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.*

@CompileStatic
@SuppressWarnings(["GrMethodMayBeStatic", "GroovyUnusedDeclaration"])
abstract class GdslScriptBase extends Script {

  static final String ideaVersion
  static {
    def major = ApplicationInfo.instance.majorVersion
    def minor = ApplicationInfo.instance.minorVersion
    def full = major + (minor ? ".$minor" : "")
    ideaVersion = full
  }

  final List<Pair<ContextFilter, Closure>> enhancers = []
  final MultiMap staticInfo = []
  private boolean locked = false

  abstract void scriptBody()

  def run() {
    try {
      scriptBody()
    }
    catch (InvalidVersionException ignore) {
      enhancers.clear()
    }
    locked = true
  }

  def methodMissing(String name, Object args) { DslPointcut.UNKNOWN }

  void contribute(Object cts, Closure toDo) {
    cts = handleImplicitBind(cts)

    if (cts instanceof DslPointcut) {
      assert cts.operatesOn(GroovyClassDescriptor): "A non top-level pointcut passed to contributor"
      addClassEnhancer([new PointcutContextFilter(cts)], toDo)
      return
    }

    if (cts instanceof Map) {
      cts = new Context(cts)
    }
    if (!(cts instanceof List)) {
      assert cts instanceof Context: "The contributor() argument must be a context"
      cts = [cts]
    }
    def contexts = cts.findAll { it != null } as List<Context>
    if (contexts) {
      def filters = contexts.collect { return it.filter }
      addClassEnhancer(filters, toDo)
    }
  }

  void contributor(cts, Closure toDo) { contribute(cts, toDo) }

  void assertVersion(ver) { if (!supportsVersion(ver)) throw new InvalidVersionException() }

  void scriptSuperClass(Map args) { staticInfo.putValue('scriptSuperClass', args) }

  boolean supportsVersion(ver) {
    if (ver instanceof String) {
      return StringUtil.compareVersionNumbers(ideaVersion, ver) >= 0
    }
    else if (ver instanceof Map) {
      if (ver.dsl) {
        return StringUtil.compareVersionNumbers('1.0', ver.dsl as String) >= 0
      }
      if (ver.intellij) {
        return StringUtil.compareVersionNumbers(ideaVersion, ver.intellij as String) >= 0
      }
    }
    return false
  }

  void addClassEnhancer(List<? extends ContextFilter> cts, Closure toDo) {
    assert !locked: 'Contributing to GDSL is only allowed at the top-level of the *.gdsl script'
    enhancers << Pair.create(CompositeContextFilter.compose(cts, false), toDo)
  }

  private static class InvalidVersionException extends Exception {}

  /**
   * Auxiliary methods for context definition
   */
  Scope closureScope(Map args) { return new ClosureScope(args) }

  Scope scriptScope(Map args) { return new ScriptScope(args) }

  Scope classScope(Map args) { return new ClassScope(args) }

  Scope annotatedScope(Map args) { return new AnnotatedScope(args) }

  /**
   * Context definition
   */
  def context(Map args) { return new Context(args) }

  def hasAnnotation(String annoQName) { PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName) }

  def hasField(ElementPattern fieldCondition) { PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition)) }

  def hasMethod(ElementPattern methodCondition) {
    PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition))
  }

  def bind(final Object arg) {
    DslPointcut.bind(arg)
  }

  def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 &&
        arg.keySet().iterator().next() instanceof String &&
        arg.values().iterator().next() instanceof DslPointcut) {
      return DslPointcut.bind(arg)
    }
    return arg
  }

  DslPointcut<GdslType, GdslType> subType(final Object arg) {
    DslPointcut.subType(handleImplicitBind(arg))
  }

  DslPointcut<GroovyClassDescriptor, GdslType> currentType(final Object arg) {
    DslPointcut.currentType(handleImplicitBind(arg))
  }

  DslPointcut<GroovyClassDescriptor, GdslType> enclosingType(final Object arg) {
    DslPointcut.enclosingType(handleImplicitBind(arg))
  }

  DslPointcut<Object, String> name(final Object arg) {
    DslPointcut.name(handleImplicitBind(arg))
  }

  DslPointcut<GroovyClassDescriptor, GdslMethod> enclosingMethod(final Object arg) {
    DslPointcut.enclosingMethod(handleImplicitBind(arg))
  }
}
