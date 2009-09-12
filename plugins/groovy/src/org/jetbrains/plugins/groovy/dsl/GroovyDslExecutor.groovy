package org.jetbrains.plugins.groovy.dsl

import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.GdslMetaClassProperties
import org.jetbrains.plugins.groovy.dsl.toplevel.Contributor
import org.jetbrains.plugins.groovy.dsl.augmenters.PsiAugmenter
import org.jetbrains.plugins.groovy.dsl.augmenters.PsiClassCategory

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  private final List<Closure> myScriptEnhancers = []
  private final List<Closure> myClassEnhancers = []
  private final String myFileName;

  private final List<Context> myContexts = []
  private final List<Contributor> myContributors = []

  // todo provide extensions!
  private final List<PsiAugmenter> myPsiAugmenters = [
          new PsiClassCategory()
  ]

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

    //Augment PSI interfaces with new methods
    //augmentCommonPSI()

    mc.initialize()
    script.metaClass = mc
    script.run()
  }

  def augmentCommonPSI() {
    for (aug in myPsiAugmenters) {
      final def fqn = aug.getTargetClassFqn()
      try {
        final def psiClazz = Class.forName(fqn)
        for (MetaMethod m in aug.metaClass.methods) {
          // Injecting methods to existing PsiClasses
          // Wrapper to handle an arbitrary number of parameters
          psiClazz.metaClass."${m.name}Impl" = {List args ->
                            m.invoke(aug, *args)}
          psiClass.metaClass."$m.name" = {Object[] args -> "${m.name}Impl"(args.toList())}
        }

      }
      catch (ClassNotFoundException) {
        // do nothing
      }
    }
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

