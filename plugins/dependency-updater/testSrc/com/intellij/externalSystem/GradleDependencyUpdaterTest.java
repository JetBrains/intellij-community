package com.intellij.externalSystem;

import com.intellij.buildsystem.model.DeclaredDependency;
import com.intellij.buildsystem.model.unified.UnifiedCoordinates;
import com.intellij.buildsystem.model.unified.UnifiedDependency;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class GradleDependencyUpdaterTest extends GradleDependencyUpdaterTestBase {

  @Test
  public void testGetDependencies() throws IOException {
    importProjectFromTemplate();
    assertTrue(myModifierService.supports(getModule("project")));
    List<DeclaredDependency> dependencies = myModifierService.declaredDependencies(getModule("project"));
    UnifiedCoordinates artifact = new UnifiedCoordinates("group", "artifact", "1.0");
    UnifiedCoordinates another = new UnifiedCoordinates("another", "artifact", "1.0");
    UnifiedCoordinates shortNotation = new UnifiedCoordinates("shortGroup", "shortArtifact", "1.0");

    assertEquals(artifact, dependencies.get(0).getCoordinates());
    assertEquals(another, dependencies.get(1).getCoordinates());
    assertEquals(shortNotation, dependencies.get(2).getCoordinates());

    assertTrue(dependencies.get(0).getPsiElement().textMatches("group: 'group', name: 'artifact', version: '1.0'"));
    assertTrue(dependencies.get(1).getPsiElement().textMatches("group: 'another', name: 'artifact', version: '1.0'"));
    assertTrue(dependencies.get(2).getPsiElement().textMatches("'shortGroup:shortArtifact:1.0'"));
  }

  @Test
  public void testAddDependency() throws Exception {
    importProjectFromTemplate();
    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.addDependency(getModule("project"),
                                    new UnifiedDependency("group", "artifact", "1.0", null));

    importProject();
    assertScriptChanged();
    assertModuleLibDep("project.main", "Gradle: group:artifact:1.0");
  }

  @Test
  public void testUpdateDependencyShortNotation() throws Exception {
    importProjectFromTemplate();

    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.updateDependency(getModule("project"),
                             new UnifiedDependency("group", "artifact", "1.0", null),
                             new UnifiedDependency("group", "artifact", "2.0", null));

    importProject();
    assertScriptChanged();
    assertModuleLibDeps("project.main", "Gradle: group:artifact:2.0", "Gradle: another:artifact:1.0");
  }

  @Test
  public void testUpdateDependencyLongNotation() throws Exception {
    importProjectFromTemplate();

    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.updateDependency(getModule("project"),
                             new UnifiedDependency("group", "artifact", "1.0", null),
                             new UnifiedDependency("group", "artifact", "2.0", null));

    importProject();
    assertScriptChanged();
    assertModuleLibDeps("project.main", "Gradle: group:artifact:2.0", "Gradle: another:artifact:1.0");
  }

  @Test
  public void testRemoveDependency() throws Exception {
    importProjectFromTemplate();

    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.removeDependency(getModule("project"),
                             new UnifiedDependency("group", "artifact", "1.0", null));

    importProject();
    assertScriptChanged();
    assertModuleLibDeps("project.main", "Gradle: another:artifact:1.0");
  }

  @Test
  public void testUpdateDependencyWithVariableLongNotation() throws Exception {
    importProjectFromTemplate();

    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.updateDependency(getModule("project"),
                             new UnifiedDependency("group", "artifact", "1.0", null),
                             new UnifiedDependency("group", "artifact", "2.0", null));

    importProject();
    assertScriptChanged();
  }

  @Test
  public void testUpdateDependencyWithExtVariableLongNotation() throws Exception {
    importProjectFromTemplate();

    assertTrue(myModifierService.supports(getModule("project")));
    myModifierService.updateDependency(getModule("project"),
                             new UnifiedDependency("group", "artifact", "1.0", null),
                             new UnifiedDependency("group", "artifact", "2.0", null));

    importProject();
    assertScriptChanged();
  }
}
