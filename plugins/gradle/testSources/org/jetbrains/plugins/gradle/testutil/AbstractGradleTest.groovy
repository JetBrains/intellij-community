package org.jetbrains.plugins.gradle.testutil

import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel
import org.junit.Before
import org.picocontainer.defaults.DefaultPicoContainer
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.diff.PlatformFacade
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.GradleModuleStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.GradleLibraryDependencyStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.GradleLibraryStructureChangesCalculator
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangeListener

import static org.junit.Assert.assertEquals
import org.picocontainer.MutablePicoContainer
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;

/**
 * @author Denis Zhdanov
 * @since 2/13/12 10:43 AM
 */
public abstract class AbstractGradleTest {
  
  GradleProjectStructureChangesModel changesModel
  GradleProjectStructureTreeModel treeModel
  def gradle
  def intellij
  def changes
  def treeChecker
  def container

  @Before
  public void setUp() {
    gradle = new GradleProjectBuilder()
    intellij = new IntellijProjectBuilder()
    changes = new ChangeBuilder()
    treeChecker = new ProjectStructureChecker()
    container = new DefaultPicoContainer()
    container.registerComponentInstance(Project, intellij.project)
    container.registerComponentInstance(PlatformFacade, intellij.platformFacade as PlatformFacade)
    container.registerComponentImplementation(GradleProjectStructureChangesModel)
    container.registerComponentImplementation(GradleProjectStructureTreeModel)
    container.registerComponentImplementation(GradleProjectStructureHelper)
    container.registerComponentImplementation(GradleStructureChangesCalculator, GradleProjectStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleStructureChangesCalculator)
    container.registerComponentImplementation(GradleLibraryDependencyStructureChangesCalculator)
    container.registerComponentImplementation(GradleLibraryStructureChangesCalculator)
    container.registerComponentImplementation(GradleEntityIdMapper)
    configureContainer(container)

    changesModel = container.getComponentInstance(GradleProjectStructureChangesModel) as GradleProjectStructureChangesModel
  }

  protected void configureContainer(MutablePicoContainer container) {
  }
  
  @SuppressWarnings("GroovyAssignabilityCheck")
  protected def init(map = [:]) {
    treeModel = container.getComponentInstance(GradleProjectStructureTreeModel) as GradleProjectStructureTreeModel
    changesModel.addListener({ old, current ->
                               treeModel.update(current)
                               treeModel.processObsoleteChanges(ContainerUtil.subtract(old, current));
    } as GradleProjectStructureChangeListener)
    setState(map, false)
    treeModel.rebuild()
    changesModel.update(gradle.project)
  }

  protected def setState(map, update = true) {
    map.intellij?.delegate = intellij
    map.intellij?.call()
    map.gradle?.delegate = gradle
    map.gradle?.call()
    if (update) {
      changesModel.update(gradle.project)
    }
  }
  
  protected def checkChanges(Closure c) {
    c.delegate = changes
    def expected = c()
    if (!expected) {
      expected = [].toSet()
    }
    assertEquals(expected, changesModel.changes)
  }

  protected def checkTree(c) {
    def nodeBuilder = new NodeBuilder()
    c.delegate = nodeBuilder
    def expected = c()
    treeChecker.check(expected, treeModel.root)
  }
}
