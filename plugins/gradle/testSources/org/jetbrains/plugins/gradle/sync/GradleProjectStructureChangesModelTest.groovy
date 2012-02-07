package org.jetbrains.plugins.gradle.sync;


import com.intellij.openapi.project.Project
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.gradle.testutil.ChangeBuilder
import org.jetbrains.plugins.gradle.testutil.GradleProjectBuilder
import org.jetbrains.plugins.gradle.testutil.IntellijProjectBuilder
import org.jetbrains.plugins.gradle.testutil.ProjectStructureChecker
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
    container.registerComponentImplementation(GradleLibraryStructureChangesCalculator)
    container.registerComponentImplementation(GradleProjectStructureTreeModel)
    container.registerComponentImplementation(GradleProjectStructureHelper)
    
    changesModel = container.getComponentInstance(GradleProjectStructureChangesModel) as GradleProjectStructureChangesModel
  }

  @Test
  public void processObsoleteGradleLocalChange() {
    // Configure initial projects state.
    init(
      gradle {
        module {
          dependencies {
            lib(name: "lib1")
            lib(name: "lib2")
            lib(name: "lib3")
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
        lib(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib3" })
    } }
    checkTree {
      project {
        module() {
          dependencies {
            lib1()
            lib2('gradle')
            lib3('gradle')
    } } } }

    // Add the same library at intellij side. Expecting to have the only change now.
    intellij {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2")
    } } }
    changesModel.update(gradle.project)
    checkChanges {
      presence {
        lib(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib3" })
    } }
    checkTree {
      project {
        module() {
          dependencies {
            lib1()
            lib2()
            lib3('gradle')
    } } } }
    
    // Remove the 'gradle local' dependency.
    gradle {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2")
    } } }
    
    // Apply the changed project state and check if it's correctly parsed.
    changesModel.update(gradle.project)
    checkChanges { } // no changes.
    assertEquals([].toSet(), changesModel.changes)
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2()
    } } } }
  }
  
  @Test
  public void libraryDependenciesWithDifferentPaths() {
    // Let the model has two differences in a library setup initially.
    init(
      gradle {
        module {
          dependencies {
            lib(name: "lib1")
            lib(name: "lib2", bin: ['1', '2'])
      } } },
      intellij {
        module {
          dependencies {
            lib(name: "lib1")
            lib(name: "lib2", bin: ['2', '3'])
      }}}
    )
    
    checkChanges {
      libraryConflict(entity: intellij.libraries['lib2']) {
        binaryPath(gradle: '1', intellij: null)
        binaryPath(gradle: null, intellij: '3')
    } }
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2('conflict')
    } } } }
    
    // Remove one difference from the library setup and check that the corresponding node is still marked as conflicted
    gradle {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2", bin: ['2'])
    } } }
    intellij {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2", bin: ['2', '3'])
    }}}
    changesModel.update(gradle.project)
    checkChanges {
      libraryConflict(entity: intellij.dependencies.values().flatten().find {it.library.name == 'lib2' }.library) {
        binaryPath(gradle: null, intellij: '3')
    } }
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2('conflict')
    } } } }
    
    // Match the remaining change and check that the corresponding node is not marked as conflicted anymore.
    gradle {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2", bin: ['2', '3'])
    } } }
    intellij {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2", bin: ['2', '3'])
    }}}
    changesModel.update(gradle.project)
    checkChanges { } // No changes
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2()
    } } } }
  }
  
  @SuppressWarnings("GroovyAssignabilityCheck")
  private def init(gradleProjectInit, intellijProjectInit) {
    treeModel = container.getComponentInstance(GradleProjectStructureTreeModel) as GradleProjectStructureTreeModel
    changesModel.addListener({ old, current ->
      treeModel.update(current)
      treeModel.processObsoleteChanges(ContainerUtil.subtract(old, current));
    } as GradleProjectStructureChangeListener)
    changesModel.update(gradle.project)
  }

  private def checkChanges(c) {
    c.delegate = changes
    def expected = c()
    if (!expected) {
      expected = [].toSet()
    }
    assertEquals(expected, changesModel.changes)
  }

  private def checkTree(c) {
    def nodeBuilder = new NodeBuilder()
    c.delegate = nodeBuilder
    def expected = c()
    treeChecker.check(expected, treeModel.root)
  }
}
