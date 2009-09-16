package org.jetbrains.plugins.groovy.dsl

import org.jetbrains.plugins.groovy.dsl.toplevel.Contributor
import org.jetbrains.plugins.groovy.dsl.toplevel.GdslMetaClassProperties
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Any

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  private final List<Closure> myScriptEnhancers = []
  private final List<Closure> myClassEnhancers = []
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
      if (p.getType() == Closure.class ||
          p.getType() == Any.class) {
        mc."$p.name" = p.getProperty(enhancer)
      }
    }

    mc.initialize()
    script.metaClass = mc
    script.run()
  }

  public def runEnhancer(code, delegate) {
    def copy = code.clone()
    copy.delegate = delegate
    copy()
  }

  def addClassEnhancer(Closure cl) {
    myClassEnhancers << cl
  }

  def addScriptEnhancer(Closure cl) {
    myScriptEnhancers << cl
  }

  public def runContributor(Contributor cb, ClassDescriptor cd, delegate) {
    cb.getApplyFunction(delegate, cd.getPlace())()
  }

  def processVariants(ClassDescriptor descriptor, consumer) {
    for (e in (descriptor instanceof ScriptDescriptor ? myScriptEnhancers : myClassEnhancers)) {
      e(descriptor, consumer)
    }
  }

  def String toString() {
    return "${super.toString()}; file = $myFileName";
  }

}