// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class GlobalSearchScopeTest extends HeavyPlatformTestCase {
  public void testUniteDirectorySearchScopeDoesNotSOE() throws Exception {
    VirtualFile genRoot = getVirtualFile(createTempDir("genSrcRoot"));
    VirtualFile srcRoot = getVirtualFile(createTempDir("srcRoot"));
    VirtualFile child = createChildDirectory(srcRoot, "child");
    GlobalSearchScope childScope = GlobalSearchScopesCore.directoryScope(getProject(), child, true);

    GlobalSearchScope directoryScope = GlobalSearchScopesCore.directoryScope(getProject(), srcRoot, true);
    GlobalSearchScope scope = GlobalSearchScope.EMPTY_SCOPE.uniteWith(directoryScope);
    assertSame(scope, directoryScope);
    scope = scope.uniteWith(directoryScope);
    assertSame(scope, directoryScope);

    scope = scope.uniteWith(childScope);
    assertSame(scope, directoryScope);

    GlobalSearchScope s = childScope;
    int N = 1000;
    VirtualFile[] d = new VirtualFile[N];
    for (int i=0; i< N;i++) {
      d[i] = createChildDirectory(srcRoot, "d"+i);
      GlobalSearchScope united = s.uniteWith(GlobalSearchScopesCore.directoryScope(getProject(), d[i], true));
      assertNotSame(s, united);
      s = united;
      assertTrue(s instanceof GlobalSearchScopesCore.DirectoriesScope);
    }
    for (VirtualFile file : d) {
      VirtualFile f = createChildData(file, "f");
      assertTrue(s.contains(f));
    }
    assertFalse(s.contains(genRoot));

    assertSame(s.uniteWith(childScope), s);
    assertSame(s.uniteWith(s), s);
  }

  public void testNotScope() {
    VirtualFile moduleRoot = getTempDir().createVirtualDir();
    ModuleRootModificationUtil.addContentRoot(getModule(), moduleRoot.getPath());

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(getProject());
    assertFalse(projectScope.isSearchInLibraries());
    assertTrue(projectScope.isSearchInModuleContent(getModule()));
    assertTrue(projectScope.contains(moduleRoot));

    GlobalSearchScope notProjectScope = GlobalSearchScope.notScope(projectScope);
    assertTrue(notProjectScope.isSearchInLibraries());
    assertFalse(notProjectScope.contains(moduleRoot));

    GlobalSearchScope allScope = GlobalSearchScope.allScope(getProject());
    assertTrue(allScope.isSearchInLibraries());
    assertTrue(allScope.contains(moduleRoot));

    GlobalSearchScope notAllScope = GlobalSearchScope.notScope(allScope);
    assertFalse(notAllScope.contains(moduleRoot));
  }

  public void testIntersectionPreservesOrderInCaseClientsWantToPutCheaperChecksFirst() {
    AtomicInteger targetCalled = new AtomicInteger();
    GlobalSearchScope alwaysTrue = new DelegatingGlobalSearchScope(new EverythingGlobalScope()) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return true;
      }
    };
    GlobalSearchScope target = new DelegatingGlobalSearchScope(new EverythingGlobalScope()) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        targetCalled.incrementAndGet();
        return true;
      }
    };
    GlobalSearchScope trueIntersection = target.intersectWith(alwaysTrue);

    VirtualFile file1 = getTempDir().createVirtualFile("file1");
    VirtualFile file2 = getTempDir().createVirtualFile("file2");

    assertTrue(trueIntersection.contains(file2));
    assertEquals(1, targetCalled.get());

    assertFalse(GlobalSearchScope.fileScope(myProject, file1).intersectWith(trueIntersection).contains(file2));
    assertEquals(1, targetCalled.get());
  }

  public void testDirScopeSearchInLibraries() throws IOException {
    VirtualFile libRoot = getVirtualFile(createTempDir("libRoot"));
    VirtualFile contentRoot = getVirtualFile(createTempDir("contentRoot"));

    PsiTestUtil.removeAllRoots(getModule(), null);
    PsiTestUtil.addContentRoot(getModule(), contentRoot);
    PsiTestUtil.addLibrary(getModule(), libRoot.getPath());

    assertTrue(GlobalSearchScopesCore.directoryScope(myProject, libRoot, true).isSearchInLibraries());
    assertTrue(GlobalSearchScopesCore.directoriesScope(myProject, true, libRoot, contentRoot).isSearchInLibraries());
  }

  public void testUnionWithEmptyScopeMustNotAffectCompare() {
    VirtualFile moduleRoot = getTempDir().createVirtualDir();
    assertNotNull(moduleRoot);
    PsiTestUtil.addSourceRoot(getModule(), moduleRoot);
    VirtualFile moduleRoot2 = getTempDir().createVirtualDir();
    assertNotNull(moduleRoot2);
    PsiTestUtil.addSourceRoot(getModule(), moduleRoot2);

    GlobalSearchScope modScope = getModule().getModuleContentScope();
    int compare = modScope.compare(moduleRoot, moduleRoot2);
    assertTrue(compare != 0);
    GlobalSearchScope union = modScope.uniteWith(GlobalSearchScope.EMPTY_SCOPE);
    int compare2 = union.compare(moduleRoot, moduleRoot2);
    assertEquals(compare, compare2);

    assertEquals(modScope.compare(moduleRoot2, moduleRoot), union.compare(moduleRoot2, moduleRoot));
  }

  public void testIsInScopeDoesNotAcceptRandomNonPhysicalFilesByDefault() {
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(PlainTextLanguage.INSTANCE, "");
    VirtualFile vFile = file.getViewProvider().getVirtualFile();

    assertFalse(GlobalSearchScope.allScope(myProject).contains(vFile));
    assertFalse(PsiSearchScopeUtil.isInScope(GlobalSearchScope.allScope(myProject), file));

    assertTrue(file.getResolveScope().contains(vFile));
    assertTrue(PsiSearchScopeUtil.isInScope(file.getResolveScope(), file));
  }

  public void testUnionWithEmptyAndUnion() {
    GlobalSearchScope scope = GlobalSearchScope.EMPTY_SCOPE.uniteWith(GlobalSearchScope.EMPTY_SCOPE);
    assertEquals(GlobalSearchScope.EMPTY_SCOPE, scope);
    GlobalSearchScope scope2 = GlobalSearchScope.union(new GlobalSearchScope[]{GlobalSearchScope.EMPTY_SCOPE, GlobalSearchScope.EMPTY_SCOPE});
    assertEquals(GlobalSearchScope.EMPTY_SCOPE, scope2);
    GlobalSearchScope p = GlobalSearchScope.projectScope(getProject());
    GlobalSearchScope scope3 = GlobalSearchScope.union(new GlobalSearchScope[]{GlobalSearchScope.EMPTY_SCOPE, p, GlobalSearchScope.EMPTY_SCOPE});
    assertEquals(p, scope3);

    GlobalSearchScope m = GlobalSearchScope.moduleScope(getModule());
    GlobalSearchScope pm = m.uniteWith(p);
    Assert.assertNotEquals(m, pm);

    GlobalSearchScope scope4 = GlobalSearchScope.union(new GlobalSearchScope[]{GlobalSearchScope.EMPTY_SCOPE, p, GlobalSearchScope.EMPTY_SCOPE, pm, m});
    assertEquals(pm, scope4);
  }
}