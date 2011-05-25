package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.GdslMetaClassProperties

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  static final def cats = PsiEnhancerCategory.EP_NAME.extensions.collect { it.class }
  final List<Pair<ContextFilter, Closure>> enhancers = []

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
    def script = shell.parse(text, fileName)

    def mc = new ExpandoMetaClass(script.class, false)
    def enhancer = new GdslMetaClassProperties(this)

    mc.methodMissing = { String name, Object args ->
      return new Context([:])
    }

    // Fill script with necessary properties
    def properties = enhancer.metaClass.properties
    for (MetaProperty p in properties) {
      if (p.getType() == Closure.class) {
        mc."$p.name" = p.getProperty(enhancer)
      }
    }


    def contribute = {cts, Closure toDo ->
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

    mc.supportsVersion = { String ver ->
      StringUtil.compareVersionNumbers(ideaVersion, ver) >= 0
    }

    mc.initialize()
    script.metaClass = mc
    script.run()

    locked = true
  }

  def addClassEnhancer(List<ContextFilter> cts, Closure toDo) {
    assert !locked : 'Contributing to GDSL is only allowed at the top-level of the *.gdsl script'
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
