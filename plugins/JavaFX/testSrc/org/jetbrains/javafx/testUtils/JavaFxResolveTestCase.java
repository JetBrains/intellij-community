package org.jetbrains.javafx.testUtils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxResolveTestCase extends JavaFxLightFixtureTestCase {
  @NonNls protected static final String MARKER = "<ref>";

  protected PsiReference configureByFile(@TestDataFile final String filePath) {
    final VirtualFile testDataRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(getTestDataPath()));
    assertNotNull(testDataRoot);
    final VirtualFile file = testDataRoot.findFileByRelativePath(filePath);
    assertNotNull(file);

    String fileText;
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(file));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    final int offset = fileText.indexOf(MARKER);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());
    final String finalFileText = fileText;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myFixture.configureByText(new File(filePath).getName(), finalFileText);
      }
    });
    return myFixture.getFile().findReferenceAt(offset);
  }


  @Nullable
  protected abstract PsiElement doResolve();

  protected <T extends PsiElement> T assertResolvesTo(final Class<T> aClass, final String name) {
    return assertResolvesTo(aClass, name, null);
  }

  protected <T extends PsiElement> T assertResolvesTo(final Class<T> aClass,
                                                         final String name,
                                                         final String containingFilePath) {
    final PsiElement element = doResolve();
    assertInstanceOf(element, aClass);
    assertEquals(name, ((PsiNamedElement) element).getName());
    if (containingFilePath != null) {
      assertEquals(containingFilePath, element.getContainingFile().getVirtualFile().getPath());
    }
    return (T)element;
  }

  protected void assertUnresolved() {
    assertNull(doResolve());
  }
}
