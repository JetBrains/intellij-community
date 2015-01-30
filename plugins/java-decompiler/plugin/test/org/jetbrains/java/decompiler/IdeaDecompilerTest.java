/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler;

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.Alarm;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class IdeaDecompilerTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("java-decompiler") + "/plugin/testData");
  }

  public void testSimple() {
    String path = PlatformTestUtil.getRtJarPath() + "!/java/lang/String.class";
    VirtualFile file = getTestFile(path);
    String decompiled = new IdeaDecompiler().getText(file).toString();
    assertTrue(decompiled, decompiled.startsWith(IdeaDecompiler.BANNER + "package java.lang;\n"));
    assertTrue(decompiled, decompiled.contains("public final class String"));
    assertTrue(decompiled, decompiled.contains("@deprecated"));
    assertTrue(decompiled, decompiled.contains("private static class CaseInsensitiveComparator"));
    assertFalse(decompiled, decompiled.contains("{ /* compiled code */ }"));
    assertFalse(decompiled, decompiled.contains("synthetic"));
  }

  public void testStubCompatibility() {
    Registry.get("decompiler.dump.original.lines").setValue(true);
    String path = PlatformTestUtil.getRtJarPath() + "!/java";
    VirtualFile dir = getTestFile(path);
    doTestStubCompatibility(dir);
    Registry.get("decompiler.dump.original.lines").setValue(false);
  }

  private void doTestStubCompatibility(VirtualFile root) {
    VfsUtilCore.visitChildrenRecursively(root, new MyFileVisitor());
  }

  public void testNavigation() {
    myFixture.openFileInEditor(getTestFile("Navigation.class"));
    doTestNavigation(11, 14, 14, 10);  // to "m2()"
    doTestNavigation(15, 21, 14, 17);  // to "int i"
    doTestNavigation(16, 28, 15, 13);  // to "int r"
  }

  public void testHighlighting() {
    myFixture.openFileInEditor(getTestFile("Navigation.class"));
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(new Runnable() {
      public void run() {
        myFixture.getEditor().getCaretModel().moveToOffset(offset(11, 14));  // m2(): usage, declaration
        assertEquals(2, myFixture.doHighlighting().size());
        myFixture.getEditor().getCaretModel().moveToOffset(offset(15, 21));  // int i: usage, declaration
        assertEquals(2, myFixture.doHighlighting().size());
        myFixture.getEditor().getCaretModel().moveToOffset(offset(16, 28));  // int r: usage, declaration
        assertEquals(2, myFixture.doHighlighting().size());
        myFixture.getEditor().getCaretModel().moveToOffset(offset(19, 24));  // throws: declaration, m4() call
        assertEquals(2, myFixture.doHighlighting().size());
      }
    });
  }

  private VirtualFile getTestFile(String name) {
    String path = FileUtil.isAbsolute(name) ? name : myFixture.getTestDataPath() + "/" + name;
    VirtualFileSystem fs = path.contains(URLUtil.JAR_SEPARATOR) ? StandardFileSystems.jar() : StandardFileSystems.local();
    VirtualFile file = fs.refreshAndFindFileByPath(path);
    assertNotNull(path, file);
    return file;
  }

  private void doTestNavigation(int line, int column, int expectedLine, int expectedColumn) {
    PsiElement target = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset(line, column));
    assertTrue(String.valueOf(target), target instanceof Navigatable);
    ((Navigatable)target).navigate(true);
    int expected = offset(expectedLine, expectedColumn);
    assertEquals(expected, myFixture.getCaretOffset());
  }

  private int offset(int line, int column) {
    return myFixture.getEditor().getDocument().getLineStartOffset(line - 1) + column - 1;
  }

  public void testLineNumberMapping() {
    RegistryValue value = Registry.get("decompiler.use.line.mapping");
    boolean old = value.asBoolean();
    try {
      value.setValue(true);

      VirtualFile file = getTestFile("LineNumbers.class");
      assertNull(file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY));

      new IdeaDecompiler().getText(file);

      LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
      assertNotNull(mapping);
      assertEquals(11, mapping.bytecodeToSource(3));
      assertEquals(23, mapping.bytecodeToSource(13));
    }
    finally {
      value.setValue(old);
    }
  }

  public void testPerformance() {
    final IdeaDecompiler decompiler = new IdeaDecompiler();
    final VirtualFile file = getTestFile(PlatformTestUtil.getRtJarPath() + "!/javax/swing/JTable.class");
    PlatformTestUtil.startPerformanceTest("decompiling JTable.class", 2500, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        decompiler.getText(file);
      }
    }).cpuBound().assertTiming();
  }

  public void testCancellation() {
    if (GraphicsEnvironment.isHeadless()) {
      System.err.println("** skipped in headless env.");
      return;
    }

    final VirtualFile file = getTestFile(PlatformTestUtil.getRtJarPath() + "!/javax/swing/JTable.class");

    final IdeaDecompiler decompiler = (IdeaDecompiler)ClassFileDecompilers.find(file);
    assertNotNull(decompiler);

    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, getProject());
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        ProgressIndicator progress = decompiler.getProgress(file);
        if (progress != null) {
          progress.cancel();
        }
        else {
          alarm.addRequest(this, 200, ModalityState.any());
        }
      }
    }, 750, ModalityState.any());

    try {
      FileDocumentManager.getInstance().getDocument(file);
      fail("should have been cancelled");
    }
    catch (ProcessCanceledException ignored) { }
  }

  private class MyFileVisitor extends VirtualFileVisitor {
    @Override
    public boolean visitFile(@NotNull VirtualFile file) {
      if (file.isDirectory()) {
        System.out.println(file.getPath());
      }
      else if (file.getFileType() == StdFileTypes.CLASS && !file.getName().contains("$")) {
        PsiFile clsFile = getPsiManager().findFile(file);
        assertNotNull(file.getPath(), clsFile);
        PsiElement mirror = ((ClsFileImpl)clsFile).getMirror();
        String decompiled = mirror.getText();
        assertTrue(file.getPath(), decompiled.contains(file.getNameWithoutExtension()));

        // check that no mapped line number is on an empty line
        String prefix = "// ";
        for (String s : decompiled.split("\n")) {
          int pos = s.indexOf(prefix);
          if (pos == 0 && prefix.length() < s.length() && Character.isDigit(s.charAt(prefix.length()))) {
            fail("Incorrect line mapping in file " + file.getPath() + " line: " + s);
          }
        }
      }
      else if (ArchiveFileType.INSTANCE.equals(file.getFileType())) {
        VirtualFile jarFile = StandardFileSystems.getJarRootForLocalFile(file);
        if (jarFile != null) {
          VfsUtilCore.visitChildrenRecursively(jarFile, new MyFileVisitor());
        }
      }
      return true;
    }
  }
}
