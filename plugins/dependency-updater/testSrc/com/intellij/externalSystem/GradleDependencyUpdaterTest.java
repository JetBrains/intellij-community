package com.intellij.externalSystem;

import com.intellij.buildsystem.model.unified.UnifiedDependency;
import org.junit.Test;

public class GradleDependencyUpdaterTest extends GradleDependencyUpdaterTestBase {

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
