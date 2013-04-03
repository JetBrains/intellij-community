package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.externalSystem.model.project.change.ContentRootPresenceChange
import com.intellij.openapi.externalSystem.model.project.change.LibraryDependencyPresenceChange
import com.intellij.openapi.externalSystem.model.project.change.ModuleDependencyPresenceChange
import com.intellij.openapi.externalSystem.model.project.change.OutdatedLibraryVersionChange
import com.intellij.openapi.externalSystem.model.project.change.JarPresenceChange
import com.intellij.openapi.externalSystem.model.project.change.ModulePresenceChange

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
        changes.addAll attributes.gradle.collect { new ModulePresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new ModulePresenceChange(null, it)}
        return changes
      case "moduleDependency":
        changes.addAll attributes.gradle.collect { new ModuleDependencyPresenceChange(it, null) }
        changes.addAll attributes.intellij.collect { new ModuleDependencyPresenceChange(null, it) }
        return changes
      case "library":
        changes.addAll attributes.gradle.collect { new LibraryDependencyPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new LibraryDependencyPresenceChange(null, it)}
        return changes
      case "libraryDependency":
        changes.addAll attributes.gradle.collect { new LibraryDependencyPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new LibraryDependencyPresenceChange(null, it)}
        return changes
      case "libraryConflict":
        def library = attributes.entity
        if (!library) {
          throw new IllegalArgumentException("No entity is defined for the library conflict change. Known attributes: $attributes")
        }
        return library
      case "contentRoot":
        changes.addAll attributes.gradle.collect { new ContentRootPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new ContentRootPresenceChange(null, it)}
        return changes
      case "jar":
        changes.addAll attributes.gradle.collect { new JarPresenceChange(it, null) }
        changes.addAll attributes.intellij.collect { new JarPresenceChange(null, it) }
        return changes
      case "libraryVersion":
        changes.add(new OutdatedLibraryVersionChange(
          attributes.name, attributes.gradleLibraryId, attributes.gradleVersion, attributes.ideLibraryId, attributes.ideVersion
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
}
