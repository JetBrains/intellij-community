package org.jetbrains.plugins.gradle.testutil

import com.intellij.pom.java.LanguageLevel

/**
 * @author Denis Zhdanov
 * @since 1/25/12 4:06 PM
 */
public abstract class AbstractProjectBuilder extends BuilderSupport {  
  
  public static final def SAME_TOKEN = "same"
  private static int COUNTER
  
  def project
  def modules = []
  /** Holds (library name; library) pairs for the active configuration. */
  def libraries = [:]
  /**
   * Holds (library name; library) pairs for the whole test. I.e. there is a possible case that we define particular configuration
   * initially and the adjust it. We need to use the same library instance then in order to pass hashCode()/equals() checks then.
   * This map works as a test-wide storage.
   */
  def librariesCache = [:]
  def dependencies = [:].withDefault {[]}

  @Override
  protected void setParent(Object parent, Object child) {
  }

  @Override
  protected Object createNode(Object name) {
    createNode(name, [:])
  }

  @Override
  protected Object createNode(Object name, Object value) {
    createNode(name, [name: value])
  }

  @Override
  protected Object createNode(Object name, Map attributes, Object value) {
    createNode(name, [name: value] + attributes)
  }
  
  @Override
  protected Object createNode(Object name, Map attributes) {
    switch (name) {
      case "dependencies": return current // Assuming that 'current' is a module object
      case "project":
        reset()
        return project = createProject(attributes.name?: same, attributes.langLevel?: LanguageLevel.JDK_1_6)
      case "module": def module = createModule(attributes.name?: same); modules << module; return module
      case "library":
        if (current == project) {
          // Library.
          return getLibrary(attributes)
        }
        else {
          // Library dependency.
          def module = current
          def dep = createLibraryDependency(module, getLibrary(attributes))
          dependencies[module] << dep
          return dep
        }
    }
  }

  protected abstract def createProject(String name, LanguageLevel languageLevel)
  protected abstract def createModule(String name)
  protected abstract def createLibrary(String name, Map paths)
  protected abstract def applyLibraryPaths(library, Map paths)
  protected abstract def createLibraryDependency(module, library)

  protected String getUnique() { "./${COUNTER++}" }
  protected String getSame() { SAME_TOKEN }

  private def getLibrary(Map attributes) {
    def name = attributes.name?: same
    def result = libraries[name]
    if (result) return result
    result = librariesCache[name]
    if (result) {
      applyLibraryPaths(result, attributes)
    }
    else {
      result = createLibrary(name, attributes.withDefault { /* empty paths*/ [] })
      librariesCache[name] = result
    }
    libraries[name] = result
    result
  }
  
  private def reset() {
    [modules, dependencies, libraries]*.clear()
  }
}
