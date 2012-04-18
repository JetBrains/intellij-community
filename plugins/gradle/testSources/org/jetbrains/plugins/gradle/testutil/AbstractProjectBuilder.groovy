package org.jetbrains.plugins.gradle.testutil

import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.roots.DependencyScope

/**
 * @author Denis Zhdanov
 * @since 1/25/12 4:06 PM
 */
public abstract class AbstractProjectBuilder extends BuilderSupport {  
  
  public static final def SAME_TOKEN = "same"
  private static int COUNTER
  
  def project
  
  /** [module name; module] */
  def modules = [:]

  /** [module; content roots] */
  def contentRoots = [:].withDefault { [] }
  
  /** Holds (library name; library) pairs for the active configuration. */
  def libraries = [:]

  /** [module; dependency list] */
  def libraryDependencies = [:].withDefault { [] }

  /** [module; dependency list] */
  def moduleDependencies = [:].withDefault { [] }
  
  /**
   * Holds (library name; library) pairs for the whole test. I.e. there is a possible case that we define particular configuration
   * initially and the adjust it. We need to use the same library instance then in order to pass hashCode()/equals() checks then.
   * This map works as a test-wide storage.
   */
  def librariesCache = [:]
  
  /** [module name; module] */
  def modulesCache = [:]

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

  @SuppressWarnings("GroovyUnusedCatchParameter")
  @Override
  protected Object createNode(name, Map attributes) {
    switch (name) {
      case "dependencies": return current // Assuming that 'current' is a module object
      case "project":
        clear()
        return project = createProject(attributes.name?: same, attributes.langLevel?: LanguageLevel.JDK_1_6)
      case "contentRoot":
        def contentRoot = createContentRoot(current, attributes.name, attributes)
        contentRoots[current] << contentRoot
        return contentRoot
      case "module":
      case "library":
        def n = StringUtil.capitalize(name)
        if (current == project) {
          // Not a dependency.
          return "get$n"(attributes)
        }
        def ownerModule = current
        def scope
        try {
          scope = DependencyScope.valueOf(attributes.scope.toUpperCase())
        }
        catch (Exception e) {
          scope = DependencyScope.COMPILE
        }
        boolean exported = attributes.exported
        def dep = "create${n}Dependency"(ownerModule, "get$n"(attributes), scope, exported)
        "get${n}Dependencies"()[ownerModule] << dep
        return dep
    }
  }

  protected abstract def createProject(String name, LanguageLevel languageLevel)
  protected abstract def createModule(String name)
  protected abstract def registerModule(module)
  protected abstract def createContentRoot(module, rootPath, Map paths)
  protected abstract def createLibrary(String name, Map paths)
  protected abstract def applyLibraryPaths(library, Map paths)
  protected abstract def createLibraryDependency(module, library, scope, boolean exported)
  protected abstract def createModuleDependency(ownerModule, targetModule, scope, boolean exported)
  protected abstract def reset();

  protected String getUnique() { "./${COUNTER++}" }
  protected String getSame() { SAME_TOKEN }

  private def getModule(Map attributes) {
    def name = attributes.name?: same
    def result = modules[name]
    if (result) return result
    result = modulesCache[name]
    if (result) {
      registerModule(result)
    }
    else {
      result = createModule(name)
      modulesCache[name] = result
    }
    modules[name] = result
    result
  }
  
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

  private def clear() {
    reset()
    [modules, contentRoots, libraryDependencies, libraries, moduleDependencies]*.clear()
  }
}
