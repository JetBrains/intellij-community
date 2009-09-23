package org.jetbrains.plugins.groovy.dsl

/**
 * @author peter
 */

interface GroovyEnhancerConsumer {
  @Delegate void property(String name, String type)
  void method(String name, String type, LinkedHashMap<String, String> parameters)
}