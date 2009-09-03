package org.jetbrains.plugins.groovy.dsl

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  private final List<Closure> myScriptEnhancers = [];
  private final List<Closure> myClassEnhancers = [];
  private final String myFileName;

  public GroovyDslExecutor(String text, String fileName) {
    myFileName = fileName

    def shell = new GroovyShell()
    def script = shell.parse(text, fileName)

    def mc = new ExpandoMetaClass(script.class, false)


    mc.enhanceScript = {Map args, Closure enh ->
      enh.resolveStrategy = Closure.DELEGATE_FIRST
      myScriptEnhancers << {
        ScriptDescriptor wrapper, GroovyEnhancerConsumer c ->
        if (args.extension == wrapper.extension) {
          runEnhancer enh, new EnhancerDelegate(consumer:c)
        }
      }
    }

    mc.enhanceClass = {Map args, Closure enh ->
      enh.resolveStrategy = Closure.DELEGATE_FIRST
      myClassEnhancers << {
        ClassDescriptor cw, GroovyEnhancerConsumer c ->
        if (!args.className || cw.getQualifiedName() == args.className) {
          runEnhancer enh, new EnhancerDelegate(consumer:c)
        }
      }

    }

    mc.initialize()
    script.metaClass = mc
    script.run()
  }

  def runEnhancer(code, delegate) {
    def copy = code.clone()
    copy.delegate = delegate
    copy()
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

  def property(Map args) {
    consumer.property(args.name, stringifyType(args.type))
  }

  private def stringifyType(type) {
    type instanceof Closure ? "groovy.lang.Closure" :
    type instanceof Map ? "java.util.Map" :
    type.toString()
  }

  def method(Map args) {
    def params = [:]
    args.params.each { name, type ->
      params[name] = stringifyType(type)
    }
    consumer.method(args.name, stringifyType(args.type), params)
  }

}

