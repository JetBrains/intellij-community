package org.jetbrains.plugins.gradle.sync;


import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.testutil.ChangeBuilder
import org.jetbrains.plugins.gradle.testutil.GradleProjectBuilder
import org.jetbrains.plugins.gradle.testutil.IntellijProjectBuilder
import org.jetbrains.plugins.gradle.testutil.ProjectStructureChecker
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.picocontainer.defaults.DefaultPicoContainer
import org.jetbrains.plugins.gradle.diff.*
import static org.junit.Assert.assertEquals

/**
 * @author Denis Zhdanov
 * @since 01/25/2012
 */
@SkipInHeadlessEnvironment
public class GradleProjectStructureChangesModelTest {

  GradleProjectStructureChangesModel changesModel
  GradleProjectStructureTreeModel treeModel
  def gradle
  def intellij
  def changes
  def treeChecker
  def container
  Disposable disposable = [dispose: { }] as Disposable
  
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
    container.registerComponentImplementation(GradleStructureChangesCalculator, GradleProjectStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleStructureChangesCalculator)
    container.registerComponentImplementation(GradleLibraryDependencyStructureChangesCalculator)
    container.registerComponentImplementation(GradleProjectStructureTreeModel)
    
    changesModel = container.getComponentInstance(GradleProjectStructureChangesModel) as GradleProjectStructureChangesModel
  }

  @After
  public void tearDown() {
    Disposer.dispose(disposable)
  }
  
  @Test
  public void mergeGradleLocalToIntellij() {
    // Configure initial projects state.
    init(
      gradle {
        module {
          dependencies {
            lib(name: "lib1")
            lib(name: "lib2")
      } } },

      intellij {
        module {
          dependencies {
            lib(name: "lib1")
      } } }
    )
    
    // Check that the initial projects state is correctly parsed.
    checkChanges {
      presence {
        lib(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib2" })
    } }
    checkTree {
      project {
        module("xxx") {
          dependencies {
            lib1()
            lib2('gradle')
    } } } }

    // Define changed project state.
    gradle {
      module {
        dependencies {
          lib(name: "lib1")
    } } }
    
    // Apply the changed project state and check if it's correctly parsed.
    changesModel.update(gradle.project)
    assertEquals([].toSet(), changesModel.changes)
    checkTree {
      project {
        module {
          dependencies {
            lib1()
    } } } }
  }

  @SuppressWarnings("GroovyAssignabilityCheck")
  private def init(gradleProjectInit, intellijProjectInit) {
    treeModel = container.getComponentInstance(GradleProjectStructureTreeModel) as GradleProjectStructureTreeModel
    changesModel.addListener({ old, current ->
      treeModel.update(current)
      treeModel.pruneObsoleteNodes(ContainerUtil.subtract(old, current));
    } as GradleProjectStructureChangeListener)
    changesModel.update(gradle.project)
  }

  private def checkChanges(c) {
    c.delegate = changes
    assertEquals(c(), changesModel.changes)
  }

  private def checkTree(c) {
    def nodeBuilder = new NodeBuilder()
    c.delegate = nodeBuilder
    def expected = c()
    treeChecker.check(expected, treeModel.root)
  }
}
