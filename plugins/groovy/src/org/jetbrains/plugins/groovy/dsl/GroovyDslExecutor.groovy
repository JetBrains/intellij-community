package org.jetbrains.plugins.groovy.dsl

import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.GdslMetaClassProperties
import org.jetbrains.plugins.groovy.dsl.toplevel.Contributor

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  private final List<Closure> myScriptEnhancers = []
  private final List<Closure> myClassEnhancers = []
  private final String myFileName;

  private final List<Context> myContexts = []
  private final List<Contributor> myContributors = []

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

  def addClassEnhancer(Closure cl) {
    myClassEnhancers << cl
  }

  def addScriptEnhancer(Closure cl) {
    myScriptEnhancers << cl
  }

  def addContext(Context ctx) {
    myContexts << ctx
  }

  def addContributor(Contributor cb) {
    myContributors << cb
  }

  public def runEnhancer(code, delegate) {
    def copy = code.clone()
    copy.delegate = delegate
    copy()
  }

  public def runContributor(Contributor cb, ClassDescriptor cd, delegate) {
    cb.getApplyFunction(delegate)(cd.getPlace())
  }

  def processVariants(ClassDescriptor descriptor, GroovyEnhancerConsumer consumer) {
    for (e in (descriptor instanceof ScriptDescriptor ? myScriptEnhancers : myClassEnhancers)) {
      e(descriptor, consumer)
    }
  }

  def String toString() {
    return "${super.toString()}; file = $myFileName";
  }

}

class EnhancerDelegate {
  GroovyEnhancerConsumer consumer

  def methodMissing(String name, args) {
    if (consumer.metaClass.respondsTo(consumer, name, args)) {
      //this.metaClass."$name" = {Object[] varArgs ->
      //  return consumer.invokeMethod(name, * varArgs)
      //}
      consumer.invokeMethod(name, args)
    }
  }


  def property(Map args) {
    consumer.property(args.name, stringifyType(args.type))
  }

  private def stringifyType(type) {
    type instanceof Closure ? "groovy.lang.Closure" :
    type instanceof Map ? "java.util.Map" :
    type.toString()
  }

  //def delegatesTo(String type) { consumer.delegatesTo(type) }

  def method(Map args) {
    def params = [:]
    args.params.each {name, type ->
      params[name] = stringifyType(type)
    }
    consumer.method(args.name, stringifyType(args.type), params)
  }

}

