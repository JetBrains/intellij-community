package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.GdslMetaClassProperties

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  static final def cats = PsiEnhancerCategory.EP_NAME.extensions.collect { it.class }
  final List<Pair<ContextFilter, Closure>> enhancers = []
  private final String myFileName;

  public GroovyDslExecutor(String text, String fileName) {
    myFileName = fileName

    def shell = new GroovyShell()
    def script = shell.parse(text, fileName)

    def mc = new ExpandoMetaClass(script.class, false)
    def enhancer = new GdslMetaClassProperties(this)

    // Fill script with necessary properties
    def properties = enhancer.metaClass.properties
    for (MetaProperty p in properties) {
      if (p.getType() == Closure.class) {
        mc."$p.name" = p.getProperty(enhancer)
      }
    }

    mc.initialize()
    script.metaClass = mc
    script.run()
  }

  def addClassEnhancer(List<ContextFilter> cts, Closure toDo) {
    enhancers << Pair.create(CompositeContextFilter.compose(cts, false), toDo)
  }

  def processVariants(GroovyClassDescriptor descriptor, consumer, ProcessingContext ctx) {
    for (pair in enhancers) {
      if (pair.first.isApplicable(descriptor, ctx)) {
        Closure f = pair.second.clone()
        f.delegate = consumer
        f.resolveStrategy = Closure.DELEGATE_FIRST

        use(cats) {
          f.call()
        }
      }
    }
  }

  def String toString() {
    return "${super.toString()}; file = $myFileName";
  }

}
