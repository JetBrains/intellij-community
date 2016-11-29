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

  CustomMembersHolder processVariants(GroovyClassDescriptor descriptor, ProcessingContext ctx, PsiType psiType) {
    if (!enhancers) return CompoundMembersHolder.EMPTY

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

  String toString() { "${super.toString()}; file = $myFileName" }

  static GroovyDslExecutor createAndRunExecutor(String text, String fileName) {
    def configuration = new CompilerConfiguration()
    configuration.scriptBaseClass = GdslScriptBase.name
    def shell = new GroovyShell(GroovyDslExecutor.classLoader, configuration)
    def script = shell.parse(text, StringUtil.sanitizeJavaIdentifier(fileName)) as GdslScriptBase
    script.run()
    return new GroovyDslExecutor(script, fileName)
  }
}
