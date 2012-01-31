package org.jetbrains.plugins.gradle.testutil

import com.intellij.pom.java.LanguageLevel;

/**
 * @author Denis Zhdanov
 * @since 1/25/12 4:06 PM
 */
public abstract class AbstractProjectBuilder extends BuilderSupport {
  
  private static final def SAME_TOKEN = "same"
  private static int COUNTER
  
  def project
  def modules = []
  def libraries = [:].withDefault { createLibrary(it.name?: same, it.paths?: [:]) }
  def dependencies = [:].withDefault {[]}

  @Override
  protected void setParent(Object parent, Object child) {
  }

  @Override
  protected Object createNode(Object name) {
    switch (name) {
      case "call": return createNode("project", [name: same])
      case "module": return createNode(name, [name: same])
      case "dependencies": return getCurrent() // Assuming that 'current' is a module object
    }
  }

  @Override
  protected Object createNode(Object name, Object value) {
    return null
  }

  @Override
  protected Object createNode(Object name, Map attributes) {
    switch (name) {
      case "project":
        reset()
        return project = createProject(attributes.name?: same, attributes.langLevel?: LanguageLevel.JDK_1_6)
      case "module": def module = createModule(attributes.name?: same); modules << module; return module
      case "lib":
        def module = getCurrent()
        def dep = createLibraryDependency(module, libraries[attributes])
        dependencies[module] << dep
        return dep
    }
  }

  @Override
  protected Object createNode(Object name, Map attributes, Object value) {
    return null
  }
  
  protected abstract def createProject(String name, LanguageLevel languageLevel)
  protected abstract def createModule(String name)
  protected abstract def createLibrary(String name, Map paths)
  protected abstract def createLibraryDependency(module, library)

  protected String getUnique() { "./${COUNTER++}" }
  protected String getSame() { SAME_TOKEN }
  
  private def reset() {
    [modules, dependencies, libraries]*.clear()
  }
}
