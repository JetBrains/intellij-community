package org.jetbrains.plugins.gradle.testutil;


import org.jetbrains.plugins.gradle.diff.GradleLibraryDependencyPresenceChange

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
    if (current == null) {
      changes = []
    }
    changes
  }

  @Override
  protected Object createNode(Object name, Object value) { changes }

  @Override
  protected Object createNode(Object name, Map attributes) {
    switch (name) {
      case "presence": return changes
      case "lib":
        changes.addAll attributes.gradle.collect { new GradleLibraryDependencyPresenceChange(it, null)}
        changes.addAll attributes.intellij.collect { new GradleLibraryDependencyPresenceChange(null, it)}
        return
    }
  }

  @Override
  protected Object createNode(Object name, Map attributes, Object value) { changes }

  @Override
  protected Object postNodeCompletion(Object parent, Object node) {
    parent == null ? changes.toSet() : node
  }
}
