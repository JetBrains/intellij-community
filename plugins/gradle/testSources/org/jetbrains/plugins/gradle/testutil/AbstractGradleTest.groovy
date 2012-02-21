package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangeListener
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel
import org.junit.Before
import org.picocontainer.MutablePicoContainer
import org.picocontainer.defaults.DefaultPicoContainer
import org.jetbrains.plugins.gradle.diff.*

import static org.junit.Assert.fail

/**
 * @author Denis Zhdanov
 * @since 2/13/12 10:43 AM
 */
public abstract class AbstractGradleTest {
  
  GradleProjectStructureChangesModel changesModel
  GradleProjectStructureTreeModel treeModel
  def gradle
  def intellij
  def changesBuilder
  def treeChecker
  def container
  private Closure changesComparator

  @Before
  public void setUp() {
    gradle = new GradleProjectBuilder()
    intellij = new IntellijProjectBuilder()
    changesBuilder = new ChangeBuilder()
    treeChecker = new ProjectStructureChecker()
    container = new DefaultPicoContainer()
    container.registerComponentInstance(Project, intellij.project)
    container.registerComponentInstance(PlatformFacade, intellij.platformFacade as PlatformFacade)
    container.registerComponentImplementation(GradleProjectStructureChangesModel)
    container.registerComponentImplementation(GradleProjectStructureTreeModel)
    container.registerComponentImplementation(GradleProjectStructureHelper)
    container.registerComponentImplementation(GradleStructureChangesCalculator, GradleProjectStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleDependencyStructureChangesCalculator)
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
                               treeModel.update(sortChanges(current))
                               treeModel.processObsoleteChanges(sortChanges(ContainerUtil.subtract(old, current)));
    } as GradleProjectStructureChangeListener)
    setState(map, false)
    treeModel.rebuild()
    changesModel.update(gradle.project)
  }

  def sortChanges(changes) {
    if (changesComparator) {
      return changes.toList().sort(changesComparator)
    }
    return changes
  }
  
  protected def setState(map, update = true) {
    map.intellij?.delegate = intellij
    map.intellij?.call()
    map.gradle?.delegate = gradle
    map.gradle?.call()
    changesComparator = map.changesSorter
    if (update) {
      changesModel.update(gradle.project)
    }
  }
  
  protected def checkChanges(Closure c) {
    changesBuilder.changes.clear()
    c.delegate = changesBuilder
    def expected = c()
    if (!expected) {
      expected = [].toSet()
    }
    def actual = new HashSet(changesModel.changes)
    if (expected == actual) {
      return
    }
    actual.removeAll(expected)
    expected.removeAll(changesModel.changes)
    def message = "Project structure changes are mismatched."
    if (expected) {
      message += "\n  Expected but not matched:"
      expected.each { message += "\n    * $it"}
    }
    if (actual) {
      message += "\n  Unexpected:"
      actual.each { message += "\n    * $it"}
    }
    fail(message)
  }

  protected def checkTree(c) {
    def nodeBuilder = new NodeBuilder()
    c.delegate = nodeBuilder
    def expected = c()
    treeChecker.check(expected, treeModel.root)
  }

  protected Closure changeByClassSorter(Map<Class<?>, Integer> rules) {
    { a, b ->
      def weightA = rules[a.class] ?: Integer.MAX_VALUE
      def weightB = rules[b.class] ?: Integer.MAX_VALUE
      if (weightA == weightB) {
        return  a.hashCode() - b.hashCode()
      }
      else {
        return weightA - weightB
      }
    }
  }
}
