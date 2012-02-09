package org.jetbrains.plugins.gradle.testutil;


import org.jetbrains.plugins.gradle.diff.GradleLibraryDependencyPresenceChange
import org.jetbrains.plugins.gradle.diff.GradleMismatchedLibraryPathChange
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.util.GradleUtil

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
    if (current == null) {
      changes = []
    }
    switch (name) {
      case "presence": return changes
      case "lib":
        changes.addAll attributes.gradle.collect { new GradleLibraryDependencyPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new GradleLibraryDependencyPresenceChange(null, it)}
        return changes
      case "libraryConflict":
        def library = attributes.entity
        if (!library) {
          throw new IllegalArgumentException("No entity is defined for the library conflict change. Known attributes: $attributes")
        }
        if (attributes.gradle) {
          return register(new GradleMismatchedLibraryPathChange(library, attributes.gradle, attributes.intellij))
        }
        return library
      case "binaryPath":
        // Assuming that we're processing library binary path conflict here
        register(new GradleMismatchedLibraryPathChange(
          current as Library, toCanonicalPath(attributes.gradle), toCanonicalPath(attributes.intellij)
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
    change
  }

  private def toCanonicalPath(String path) {
    path ? GradleUtil.toCanonicalPath(path) : path
  }
}
