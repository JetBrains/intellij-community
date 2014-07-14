package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformLangTestCase;

public class HttpVirtualFileTest extends PlatformLangTestCase {
  public void testPsiFileForRoot() throws Exception {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("http://todo.breezejs.com/");
    assertNotNull(file);
    assertNotNull(PsiManager.getInstance(myProject).findFile(file));
  }
}