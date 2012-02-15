package org.jetbrains.plugins.gradle.importing;


import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper
import org.jetbrains.plugins.gradle.testutil.AbstractGradleTest
import org.junit.Test
import org.picocontainer.MutablePicoContainer

import javax.swing.tree.TreeNode

import static org.junit.Assert.assertEquals

/**
 * @author Denis Zhdanov
 * @since 02/10/2012
 */
public class GradleLocalNodeImportHelperTest extends AbstractGradleTest {

  private GradleLocalNodeImportHelper myHelper
  
  @Override
  protected void configureContainer(MutablePicoContainer container) {
    container.registerComponentImplementation(GradleLocalNodeImportHelper)
    container.registerComponentImplementation(GradleModuleImporter)
    container.registerComponentImplementation(GradleLibraryImporter)
    container.registerComponentImplementation(GradleModuleDependencyImporter)
    container.registerComponentImplementation(GradleContentRootImporter)
  }

  @Test
  public void libraryDependencyToModule() {
    doTest {
      project {
        module('module1', transitive: true, order: 0) { // Mark that the module should be imported
          dependencies {
            library('lib1', initial: true, order: 2) // Mark that this node should be given as initially selected
            library('lib2') // No mark here. Means that the node should not be imported
        } }
        library('lib1', transitive: true, order: 1) // Mark that the library should be imported
        library('lib2') // No mark here. Means that the library should not be imported
    } }
  }

  private def doTest(Closure c) {
    myHelper = container.getComponentInstance(GradleLocalNodeImportHelper)
    def compositeBuilder = new CompositeProjectBuilder(gradleBuilder: gradle, intellijBuilder: intellij)
    c.delegate = compositeBuilder
    c.call()
    init()
    myHelper
    def nodes = collectNodes(treeModel.root as TreeNode, compositeBuilder.initial)
    def expectedEntities = compositeBuilder.expected.sort{ a, b -> a[0].compareTo(b[0]) }.collect { it[1] }
    assertEquals(expectedEntities, myHelper.deriveEntitiesToImport(nodes))
  }

  private List collectNodes(TreeNode node, initial, holder = []) {
    if (initial.contains(node.descriptor.element)) {
      holder << node
    }
    for (child in node) {
      collectNodes(child, initial, holder)
    }
    holder
  }
  
  class CompositeProjectBuilder extends BuilderSupport {
    
    def gradleBuilder
    def intellijBuilder
    def initial = [].toSet()
    def expected = []
    private def gradleLocalRoot
    private def gradleStack = new Stack()
    private def intellijStack = new Stack()

    @Override
    protected void setParent(Object parent, Object child) { }

    @Override
    protected void nodeCompleted(Object parent, Object node) {
      gradleStack.pop()
      if (node == gradleLocalRoot) {
        gradleLocalRoot = null
      }
      if (!gradleLocalRoot && !intellijStack.isEmpty()) {
        intellijStack.pop()
      }
    }

    @Override
    protected def createNode(Object name) { createNode(name, [:], '') }

    @Override
    protected def createNode(Object name, Object value) { createNode(name, [:], value) }

    @Override
    protected def createNode(Object name, Map attributes) {
      throw new UnsupportedOperationException()
    }

    @Override
    protected def createNode(Object nodeName, Map attributes, name) {
      if (!gradleStack.isEmpty()) {
        gradleBuilder.current = gradleStack.peek()
      }
      def result = gradleBuilder."$nodeName"([name: name] + attributes)
      gradleStack.push(result)
      if (attributes.transitive || attributes.initial) {
        registerTargetEntity(attributes, result)
        if (attributes.initial) {
          // We need to match the entity to the object that is stored at the gradle sync project changes tree.
          initial << GradleEntityIdMapper.mapEntityToId(result)
        }
      }
      if (!gradleLocalRoot) {
        if (!intellijStack.isEmpty()) {
          intellijBuilder.current = intellijStack.peek()
        }
        intellijStack.push(intellijBuilder."$nodeName"([name: name] + attributes))
      }
      result
    }

    private void registerTargetEntity(Map attributes, entity) {
      if (attributes.order == null) {
        throw new IllegalArgumentException("Detected 'initial' entity with undefined 'order' property: $entity")
      }
      expected << [attributes.order, entity]
      if (!gradleLocalRoot) {
        gradleLocalRoot = entity
      }
    }
  }
}