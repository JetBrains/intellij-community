package org.jetbrains.plugins.gradle.testutil

import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootPresenceChange
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyPresenceChange
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyPresenceChange
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange

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
      case "jar":
        changes.addAll attributes.gradle.collect { new GradleJarPresenceChange(it, null) }
        changes.addAll attributes.intellij.collect { new GradleJarPresenceChange(null, it) }
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
}
