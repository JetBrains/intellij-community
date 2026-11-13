// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;


import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;


/**
 * @author Max Medvedev
 */
public class GroovyMoveFileTest extends GroovyMoveTestBase {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);

  public GroovyMoveFileTest() {
    super(project, "refactoring/move/moveFile/");
  }

  @Test
  public void testMoveJavaGroovyText() {
    doTest("pack2", "A.groovy", "B.java", "C.txt");
  }

  @Override
  void perform(String newPackageName, String[] names) {
    VirtualFile pack1 = groovy.get().findFileInTempDir("pack1");
    PsiFile[] rawFiles = groovy.get().getPsiManager().findDirectory(pack1).getFiles();
    PsiFile[] files =
      ContainerUtil.filter(rawFiles, file -> ContainerUtil.or(names, name -> name.equals(file.getName()))).toArray(PsiFile.EMPTY_ARRAY);
    PsiDirectory dir = groovy.get().getPsiManager().findDirectory(groovy.get().findFileInTempDir(newPackageName));

    new MoveFilesOrDirectoriesProcessor(groovy.get().getProject(), files, dir, false, false, false, null, null).run();
  }
}
