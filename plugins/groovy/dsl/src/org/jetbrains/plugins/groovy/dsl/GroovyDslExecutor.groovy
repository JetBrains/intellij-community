package org.jetbrains.plugins.groovy.dsl

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  private final List<Closure> myScriptEnhancers = [];
  private final List<Closure> myClassEnhancers = [];

  public GroovyDslExecutor(String text, String fileName) {
    def shell = new GroovyShell()
    def script = shell.parse(text, fileName)

    def mc = new ExpandoMetaClass(script.class, false)


    mc.enhanceScript = {Map args, Closure enh ->
      enh.resolveStrategy = Closure.DELEGATE_FIRST
      myScriptEnhancers << {
        ScriptWrapper wrapper, GroovyEnhancerConsumer c ->
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
    try {
      script.run()
    }
    catch (Throwable e) {
      println e
      // Suppress all exceptions!
      // todo provide diagnostics
    }
  }

  def runEnhancer(code, delegate) {
    def copy = code.clone()
    copy.delegate = delegate
    copy()
  }

  def processScriptVariants(ScriptWrapper wrapper, GroovyEnhancerConsumer consumer) {
    for (e in myScriptEnhancers) {
      e(wrapper, consumer)
    }
  }

  def processClassVariants(ClassDescriptor descriptor, GroovyEnhancerConsumer consumer) {
    for (e in myClassEnhancers) {
      e(descriptor, consumer)
    }
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

