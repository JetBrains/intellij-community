// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.control.CompilerConfiguration
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter

@CompileStatic
@SuppressWarnings(["GrMethodMayBeStatic", "GroovyUnusedDeclaration"])
class GroovyDslExecutor {

  static final def cats = PsiEnhancerCategory.EP_NAME.extensions.collect { it.class }

  private final GdslScriptBase myScript
  private final String myFileName

  GroovyDslExecutor(GdslScriptBase script, String fileName) {
    myScript = script
    myFileName = fileName
  }

  List<Pair<ContextFilter, Closure>> getEnhancers() {
    myScript.enhancers
  }

  MultiMap getStaticInfo() {
    myScript.staticInfo
  }

  CustomMembersHolder processVariants(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    if (!enhancers) return CustomMembersHolder.EMPTY

    List<CustomMembersHolder> holders = new ArrayList<>()
    for (pair in enhancers) {
      ProgressManager.checkCanceled()
      ctx.put(DslPointcut.BOUND, null)
      if (pair.first.isApplicable(descriptor, ctx)) {
        def generator = new CustomMembersGenerator(descriptor, ctx.get(DslPointcut.BOUND))
        doRun(generator, pair.second)
        holders.addAll(generator.membersHolder)
      }
    }
    return CustomMembersHolder.create(holders)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void doRun(CustomMembersGenerator generator, Closure closure) {
    use(cats) { generator.with closure }
  }

  @Override
  String toString() { myFileName }

  static GroovyDslExecutor createAndRunExecutor(String text, String fileName) {
    def configuration = new CompilerConfiguration()
    configuration.scriptBaseClass = GdslScriptBase.name
    def shell = new GroovyShell(GroovyDslExecutor.classLoader, configuration)
    def script = shell.parse(text, StringUtil.sanitizeJavaIdentifier(fileName)) as GdslScriptBase
    script.run()
    return new GroovyDslExecutor(script, fileName)
  }
}
