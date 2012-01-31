package org.jetbrains.plugins.gradle.sync;


import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.testutil.ChangeBuilder
import org.jetbrains.plugins.gradle.testutil.GradleProjectBuilder
import org.jetbrains.plugins.gradle.testutil.IntellijProjectBuilder
import org.junit.Before
import org.junit.Test
import org.picocontainer.defaults.DefaultPicoContainer
import org.jetbrains.plugins.gradle.diff.*
import static org.junit.Assert.assertEquals

/**
 * @author Denis Zhdanov
 * @since 01/25/2012
 */
public class GradleProjectStructureChangesModelTest {

  private GradleProjectStructureChangesModel myModel;
  def gradle;
  def intellij;
  def changes;
  
  @Before
  public void setUp() {
    gradle = new GradleProjectBuilder()
    intellij = new IntellijProjectBuilder()
    changes = new ChangeBuilder()
    def container = new DefaultPicoContainer()
    container.registerComponentInstance(Project, intellij.project)
    container.registerComponentInstance(GradleProjectStructureHelper, intellij.projectStructureHelper as GradleProjectStructureHelper)
    container.registerComponentImplementation(GradleProjectStructureChangesModel)
    container.registerComponentImplementation(GradleStructureChangesCalculator, GradleProjectStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleStructureChangesCalculator)
    container.registerComponentImplementation(GradleLibraryDependencyStructureChangesCalculator)

    myModel = container.getComponentInstance(GradleProjectStructureChangesModel.class) as GradleProjectStructureChangesModel
  }
  
  @Test
  public void mergeGradleLocalToIntellij() {
    gradle {
      module {
        dependencies {
          lib(name: "lib1")
          lib(name: "lib2")
    } } }
    
    intellij {
      module {
        dependencies {
          lib(name: "lib1")
    } } }
    
    myModel.update(gradle.project)
    checkChanges {
      presence {
        lib(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib2" })
      }
    }

    gradle {
      module {
        dependencies {
          lib(name: "lib1")
    } } }
    myModel.update(gradle.project)
    assertEquals([].toSet(), myModel.changes)
  }

  private def checkChanges(c) {
    c.delegate = changes
    assertEquals(c(), myModel.changes)
  }
}
