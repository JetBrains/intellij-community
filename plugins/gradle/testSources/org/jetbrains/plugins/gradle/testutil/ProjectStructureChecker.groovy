package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.id.GradleEntityId
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode

import javax.swing.tree.DefaultMutableTreeNode
import org.jetbrains.plugins.gradle.config.GradleTextAttributes
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor
import org.junit.Assert
import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.fail
import org.jetbrains.plugins.gradle.model.GradleEntityOwner

import org.jetbrains.plugins.gradle.model.GradleEntityType

/** 
 * @author Denis Zhdanov
 * @since 1/30/12 6:04 PM
 */
class ProjectStructureChecker {
  
  static def BUILT_IN = [
    "project": Project,
    "module" : Module
  ]
  
  static def COLORS = [
    'gradle' : GradleTextAttributes.GRADLE_LOCAL_CHANGE,
    'intellij' : GradleTextAttributes.INTELLIJ_LOCAL_CHANGE,
    'conflict' : GradleTextAttributes.CHANGE_CONFLICT
  ]

  def check(Node expected, DefaultMutableTreeNode actual) {
    GradleProjectStructureNodeDescriptor descriptor = actual.userObject as GradleProjectStructureNodeDescriptor
    checkName(expected, descriptor)
    checkMarkup(expected, descriptor)
    int childIndex = 0
    for (it in expected.children().findAll { it instanceof Collection}) {
      check it as Node, actual.getChildAt(childIndex++) as DefaultMutableTreeNode
    }
    for (it in expected.children().findAll { it instanceof Node}) {
      if (childIndex >= actual.childCount) {
        fail "Expected node is not matched: $it"
      }
      check it as Node, actual.getChildAt(childIndex++) as DefaultMutableTreeNode
    }
    if (childIndex < actual.childCount) {
      def transform = { nodePath(actual.getChildAt(it) as GradleProjectStructureNode<? extends GradleEntityId>) }
      fail("Unexpected nodes detected: ${(childIndex..<actual.childCount).collect(transform, ).join(', ')}")
    }
  }

  @NotNull
  private static String nodePath(@NotNull GradleProjectStructureNode<? extends GradleEntityId> node) {
    StringBuilder result = new StringBuilder("'${node.descriptor.name}")
    for (GradleProjectStructureNode<? extends GradleEntityId> n = node.parent; n != null; n = n.parent) {
      result.append(" -> ${n.descriptor.name}")
    }
    result.append("'")
    result.toString()
  }

  private static void checkName(Node expected, GradleProjectStructureNodeDescriptor actual) {
    if (AbstractProjectBuilder.SAME_TOKEN == actual.toString() || expected.name() == actual.toString()) {
      return
    }
    def clazz = BUILT_IN[expected.name().toString()]
    if (clazz == null || !clazz.isAssignableFrom(actual.element.class)) {
      Assert.fail(
        "Failed node name check. Expected to find name '${expected.name()}'" + (clazz ? " or user object of type ${clazz.simpleName}" : "")
          + " but got: name='$actual', user object=${actual.element} "
      )
    }
  }

  static def checkMarkup(Node node, GradleProjectStructureNodeDescriptor descriptor) {
    def expectedMarkup = COLORS[node.children().find {it instanceof CharSequence}.toString()]?: GradleTextAttributes.NO_CHANGE
    assertEquals("node '$descriptor'", expectedMarkup, descriptor.attributes)

    if (descriptor.element.type != GradleEntityType.SYNTHETIC) {
      def expectedOwner = expectedMarkup == GradleTextAttributes.GRADLE_LOCAL_CHANGE ? GradleEntityOwner.GRADLE : GradleEntityOwner.INTELLIJ
      assertEquals("node '$descriptor'", expectedOwner, descriptor.element.owner)
    }
  }
}
