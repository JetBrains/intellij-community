/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiType
import com.intellij.util.ObjectUtils
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.AnnotatedScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope

import java.lang.reflect.Modifier

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  static final def cats = PsiEnhancerCategory.EP_NAME.extensions.collect { it.class }
  final List<Pair<ContextFilter, Closure>> enhancers = ObjectUtils.assertNotNull([])

  private MultiMap staticInfo = null

  private final String myFileName;
  static final String ideaVersion
  private boolean locked = false

  static {
    def major = ApplicationInfo.instance.majorVersion
    def minor = ApplicationInfo.instance.minorVersion
    def full = major + (minor ? ".$minor" : "")
    ideaVersion = full
  }

  public GroovyDslExecutor(String text, String fileName) {
    myFileName = fileName

    def shell = new GroovyShell()
    def script = shell.parse(text, StringUtil.sanitizeJavaIdentifier(fileName))

    def mc = new ExpandoMetaClass(script.class, false)

    mc.methodMissing = { String name, Object args -> return DslPointcut.UNKNOWN }

    def contribute = {cts, Closure toDo ->
      cts = handleImplicitBind(cts)

      if (cts instanceof DslPointcut) {
        assert cts.operatesOn(GroovyClassDescriptor) : "A non top-level pointcut passed to contributor"
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
      def contexts = cts.findAll { it != null } as List
      if (contexts) {
        def filters = contexts.collect { return it.filter }
        addClassEnhancer(filters, toDo)
      }
    }
    mc.contributor = contribute
    mc.contribute = contribute

    mc.bind = { arg -> DslPointcut.bind(arg) }

    for (m in DslPointcut.class.declaredMethods) {
      if ((m.modifiers & Modifier.STATIC) && (m.modifiers & Modifier.PUBLIC)) {
        def name = m.name
        if (name != 'bind') {
          mc."$name" = { arg -> org.jetbrains.plugins.groovy.dsl.DslPointcut."$name"(handleImplicitBind(arg)) }
        }
      }
    }

    mc.supportsVersion = { ver -> return supportsVersion(ver) }
    mc.assertVersion = { ver -> if (!supportsVersion(ver)) throw new InvalidVersionException() }

    mc.scriptSuperClass = { Map args ->
      if (staticInfo == null) staticInfo = MultiMap.create()
      staticInfo.putValue('scriptSuperClass', args)
    }

    oldStylePrimitives(mc)

    mc.initialize()
    script.metaClass = mc
    try {
      script.run()
    }
    catch (InvalidVersionException ignore) {
      enhancers.clear()
    }

    locked = true
  }

  private static boolean supportsVersion(ver) {
    if (ver instanceof String) {
      return StringUtil.compareVersionNumbers(ideaVersion, ver) >= 0
    }
    else if (ver instanceof Map) {
      if (ver.dsl) {
        return StringUtil.compareVersionNumbers('1.0', ver.dsl) >= 0
      }
      if (ver.intellij) {
        return StringUtil.compareVersionNumbers(ideaVersion, ver.intellij) >= 0
      }
    }
    return false
  }

  private static class InvalidVersionException extends Exception {}

  private static def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 && arg.keySet().iterator().next() instanceof String && arg.values().iterator().next() instanceof DslPointcut) {
      return DslPointcut.bind(arg)
    }
    return arg
  }

  private static void oldStylePrimitives(MetaClass mc) {
    /**
     * Context definition
     */
    mc.context = {Map args -> return new Context(args) }

    /**
     * Auxiliary methods for context definition
     */
    mc.closureScope = {Map args -> return new ClosureScope(args)}
    mc.scriptScope = {Map args -> return new ScriptScope(args)}
    mc.classScope = {Map args -> return new ClassScope(args)}

    /**
     * @since 10
     */
    mc.annotatedScope = {Map args -> return new AnnotatedScope(args)}

    mc.hasAnnotation = { String annoQName -> PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName) }
    mc.hasField = { ElementPattern fieldCondition -> PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition)) }
    mc.hasMethod = { ElementPattern methodCondition -> PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition)) }
  }

  def addClassEnhancer(List<ContextFilter> cts, Closure toDo) {
    assert !locked : 'Contributing to GDSL is only allowed at the top-level of the *.gdsl script'
    enhancers << Pair.create(CompositeContextFilter.compose(cts, false), toDo)
  }

  CompoundMembersHolder processVariants(GroovyClassDescriptor descriptor, ProcessingContext ctx, PsiType psiType) {
    CompoundMembersHolder holder = new CompoundMembersHolder()
    for (pair in enhancers) {
      ProgressManager.checkCanceled()
      ctx.put(DslPointcut.BOUND, null)
      if (pair.first.isApplicable(descriptor, ctx)) {
        def generator = new CustomMembersGenerator(descriptor, psiType, ctx.get(DslPointcut.BOUND))

        Closure f = pair.second.clone()
        f.delegate = generator
        f.resolveStrategy = Closure.DELEGATE_FIRST

        use(cats) {
          f.call()
        }

        holder.addHolder(generator.membersHolder)
      }
    }
    return holder
  }

  def String toString() {
    return "${super.toString()}; file = $myFileName";
  }

  @Nullable
  MultiMap getStaticInfo() {
    staticInfo
  }
}
