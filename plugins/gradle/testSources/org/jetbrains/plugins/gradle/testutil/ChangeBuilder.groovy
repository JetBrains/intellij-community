package org.jetbrains.plugins.gradle.testutil;


import org.jetbrains.plugins.gradle.diff.GradleLibraryDependencyPresenceChange
import org.jetbrains.plugins.gradle.diff.GradleMismatchedLibraryPathChange
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.diff.GradleModulePresenceChange
import org.jetbrains.plugins.gradle.diff.GradleModuleDependencyPresenceChange
import org.jetbrains.plugins.gradle.diff.GradleContentRootPresenceChange

/**
 * @author Denis Zhdanov
 * @since 1/26/12 3:25 PM
 */
public class ChangeBuilder extends BuilderSupport {
  
  def changes = []
  
  @Override
  protected void setParent(Object parent, Object child) {
  }

  @Override
  protected Object createNode(Object name) {
    createNode(name, [:])
  }

  @Override
  protected Object createNode(Object name, Object value) { changes }

  @Override
  protected Object createNode(Object name, Map attributes) {
    switch (name) {
      case "module":
        changes.addAll attributes.gradle.collect { new GradleModulePresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new GradleModulePresenceChange(null, it)}
        return changes
      case "moduleDependency":
        changes.addAll attributes.gradle.collect { new GradleModuleDependencyPresenceChange(it, null) }
        changes.addAll attributes.intellij.collect { new GradleModuleDependencyPresenceChange(null, it) }
        return changes
      case "library":
        changes.addAll attributes.gradle.collect { new GradleLibraryDependencyPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new GradleLibraryDependencyPresenceChange(null, it)}
        return changes
      case "libraryDependency":
        changes.addAll attributes.gradle.collect { new GradleLibraryDependencyPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new GradleLibraryDependencyPresenceChange(null, it)}
        return changes
      case "libraryConflict":
        def library = attributes.entity
        if (!library) {
          throw new IllegalArgumentException("No entity is defined for the library conflict change. Known attributes: $attributes")
        }
        return library
      case "contentRoot":
        changes.addAll attributes.gradle.collect { new GradleContentRootPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new GradleContentRootPresenceChange(null, it)}
        return changes
      case "binaryPath":
        // Assuming that we're processing library binary path conflict here
        register(new GradleMismatchedLibraryPathChange(
          current as Library, collectPaths(attributes.gradle), collectPaths(attributes.intellij)
        ))
    }
    changes
  }

  @Override
  protected Object createNode(Object name, Map attributes, Object value) { changes }

  @Override
  protected Object postNodeCompletion(Object parent, Object node) {
    parent == null ? changes.toSet() : node
  }

  protected def register(change) {
    changes << change
    changes
  }

  private def collectPaths(paths) {
    if (!paths) {
      return [].toSet()
    }
    paths.collect { toCanonicalPath(it) }.toSet()
  }
  
  private def toCanonicalPath(String path) {
    path ? GradleUtil.toCanonicalPath(path) : path
  }
}
