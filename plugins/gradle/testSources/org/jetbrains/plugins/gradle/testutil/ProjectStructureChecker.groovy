package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode

import javax.swing.tree.DefaultMutableTreeNode
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes
import com.intellij.openapi.externalSystem.ui.ProjectStructureNodeDescriptor
import org.junit.Assert
import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.fail
import com.intellij.openapi.externalSystem.model.ProjectSystemId

import com.intellij.openapi.externalSystem.model.project.ProjectEntityType

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
    'gradle'  : ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE,
    'intellij': ExternalSystemTextAttributes.IDE_LOCAL_CHANGE,
    'conflict': ExternalSystemTextAttributes.CHANGE_CONFLICT,
    'outdated': ExternalSystemTextAttributes.OUTDATED_ENTITY
  ]

  def check(Node expected, DefaultMutableTreeNode actual) {
    ProjectStructureNodeDescriptor descriptor = actual.userObject as ProjectStructureNodeDescriptor
    checkName(expected, descriptor)
    checkMarkup(expected, descriptor)
    int childIndex = 0
    for (it in expected.children().findAll { it instanceof Collection }) {
      check it as Node, actual.getChildAt(childIndex++) as DefaultMutableTreeNode
    }
    for (it in expected.children().findAll { it instanceof Node }) {
      if (childIndex >= actual.childCount) {
        fail "Expected node is not matched: $it"
      }
      check it as Node, actual.getChildAt(childIndex++) as DefaultMutableTreeNode
    }
    if (childIndex < actual.childCount) {
      def transform = { nodePath(actual.getChildAt(it) as ProjectStructureNode<? extends ProjectEntityId>) }
      fail("Unexpected nodes detected: ${(childIndex..<actual.childCount).collect(transform,).join(', ')}")
    }
  }

  @NotNull
  private static String nodePath(@NotNull ProjectStructureNode<? extends ProjectEntityId> node) {
    StringBuilder result = new StringBuilder("'${node.descriptor.name}")
    for (ProjectStructureNode<? extends ProjectEntityId> n = node.parent; n != null; n = n.parent) {
      result.append(" -> ${n.descriptor.name}")
    }
    result.append("'")
    result.toString()
  }

  private static void checkName(Node expected, ProjectStructureNodeDescriptor actual) {
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

  static def checkMarkup(Node node, ProjectStructureNodeDescriptor descriptor) {
    def expectedMarkup = COLORS[node.children().find {it instanceof CharSequence}.toString()]?: ExternalSystemTextAttributes.NO_CHANGE
    assertEquals("node '$descriptor'", expectedMarkup, descriptor.attributes)

    if (descriptor.element.type != ProjectEntityType.SYNTHETIC) {
      def expectedOwner = expectedMarkup == ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE ? ProjectSystemId.GRADLE : ProjectSystemId.IDE
      assertEquals("node '$descriptor'", expectedOwner, descriptor.element.owner)
    }
  }
}
