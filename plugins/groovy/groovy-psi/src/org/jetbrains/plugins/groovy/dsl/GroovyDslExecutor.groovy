/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiType
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.control.CompilerConfiguration
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder
import org.jetbrains.plugins.groovy.dsl.methods.GdslContextMethods
import org.jetbrains.plugins.groovy.dsl.methods.GdslPointcutMethods
import org.jetbrains.plugins.groovy.dsl.methods.GdslScopeMethods
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter

@CompileStatic
@SuppressWarnings(["GrMethodMayBeStatic", "GroovyUnusedDeclaration"])
abstract class GroovyDslExecutor extends Script implements GdslScopeMethods, GdslPointcutMethods, GdslContextMethods {
  static final def cats = PsiEnhancerCategory.EP_NAME.extensions.collect { it.class }
  static final String ideaVersion
  static {
    def major = ApplicationInfo.instance.majorVersion
    def minor = ApplicationInfo.instance.minorVersion
    def full = major + (minor ? ".$minor" : "")
    ideaVersion = full
  }

  final List<Pair<ContextFilter, Closure>> enhancers = []
  final MultiMap staticInfo = []
  boolean ok = true
  boolean locked = false
  String fileName

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

  abstract void scriptBody()

  def run() {
    try {
      scriptBody()
    }
    catch (InvalidVersionException ignore) {
      enhancers.clear()
      ok = false
    }

    locked = true
  }

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

  private static class InvalidVersionException extends Exception {}

  void addClassEnhancer(List<? extends ContextFilter> cts, Closure toDo) {
    assert !locked: 'Contributing to GDSL is only allowed at the top-level of the *.gdsl script'
    enhancers << Pair.create(CompositeContextFilter.compose(cts, false), toDo)
  }

  CustomMembersHolder processVariants(GroovyClassDescriptor descriptor, ProcessingContext ctx, PsiType psiType) {
    if (!isOk() || !enhancers) return CompoundMembersHolder.EMPTY

    CompoundMembersHolder holder = new CompoundMembersHolder()
    for (pair in enhancers) {
      ProgressManager.checkCanceled()
      ctx.put(DslPointcut.BOUND, null)
      if (pair.first.isApplicable(descriptor, ctx)) {
        def generator = new CustomMembersGenerator(descriptor, psiType, ctx.get(DslPointcut.BOUND))
        doRun(generator, pair.second)
        holder.addHolder(generator.membersHolder)
      }
    }
    return holder
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void doRun(CustomMembersGenerator generator, Closure closure) {
    use(cats) { generator.with closure }
  }

  String toString() { "${super.toString()}; file = $fileName"; }

  public static GroovyDslExecutor createAndRunExecutor(String text, String fileName) {
    def configuration = new CompilerConfiguration()
    configuration.scriptBaseClass = GroovyDslExecutor.name
    def shell = new GroovyShell(configuration)
    def script = shell.parse(text, StringUtil.sanitizeJavaIdentifier(fileName)) as GroovyDslExecutor
    script.fileName = fileName
    script.run()
    return script
  }
}
