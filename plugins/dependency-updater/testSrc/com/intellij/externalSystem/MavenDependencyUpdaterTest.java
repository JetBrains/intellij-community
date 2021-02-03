package com.intellij.externalSystem;

import com.intellij.buildsystem.model.DeclaredDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository;
import com.intellij.psi.xml.XmlTag;

import java.io.IOException;
import java.util.List;

public class MavenDependencyUpdaterTest extends MavenDependencyUpdaterTestBase {

  public void testGetDependencies() {
    List<DeclaredDependency> dependencies = myModifierService.declaredDependencies(getModule("project"));
    assertNotNull(dependencies);
    assertEquals(2, dependencies.size());

    XmlTag someArtifact = findDependencyTag("somegroup", "someartifact", "1.0");
    XmlTag another = findDependencyTag("anothergroup", "anotherArtifact", "2.0");

    assertEquals(new UnifiedDependency("somegroup", "someartifact", "1.0", null), dependencies.get(0).getUnifiedDependency());
    assertEquals(new UnifiedDependency("anothergroup", "anotherArtifact", "2.0", null), dependencies.get(1).getUnifiedDependency());

    assertEquals(someArtifact, dependencies.get(0).getPsiElement());
    assertEquals(another, dependencies.get(1).getPsiElement());

  }

  public void testAddDependency() throws IOException {
    myModifierService.addDependency(getModule("project"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  public void testAddDependencyToExistingList() throws IOException {
    myModifierService.addDependency(getModule("project"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  public void testRemoveDependency() throws IOException {
    myModifierService.removeDependency(getModule("project"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  public void testShouldAddDependencyToManagedTag() throws IOException {
    myModifierService.addDependency(getModule("m1"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }


  public void testShouldRemoveDependencyIfManaged() throws IOException {
    myModifierService.removeDependency(getModule("m1"), new UnifiedDependency("somegroup", "someartifact", "1.0", "compile"));
    assertFilesAsExpected();
  }

  public void testUpdateDependency() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  public void testUpdateManagedDependency() throws IOException {
    myModifierService.updateDependency(getModule("m1"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  public void testUpdateDependencyWithProperty() throws IOException {
    myModifierService.updateDependency(getModule("project"),
                                       new UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                       new UnifiedDependency("somegroup", "someartifact", "2.0", null)
    );
    assertFilesAsExpected();
  }

  public void testAddRepository() throws IOException {
    myModifierService.addRepository(getModule("project"), new UnifiedDependencyRepository("id", "name", "https://example.com"));
    assertFilesAsExpected();
  }

  public void testRemoveRepository() throws IOException {
    myModifierService.deleteRepository(getModule("project"), new UnifiedDependencyRepository("id", "name", "https://example.com"));
    assertFilesAsExpected();
  }
}
