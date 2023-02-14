// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.PatchLine;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@HeavyPlatformTestCase.WrapInCommand
public class PatchAutoInitTest extends HeavyPlatformTestCase {
  private static final String BINARY_FILENAME = "binary.png";

  @Override
  protected @NotNull Path getProjectDirOrFile(boolean isDirectoryBasedProject) {
    try {
      // create extra space for test with files above `getBaseDir`
      Path projectRoot = getTempDir().newPath().resolve("test/test/test/root");
      Files.createDirectories(projectRoot);
      return projectRoot;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testSimple() {
    VirtualFile root = getOrCreateProjectBaseDir();
    VirtualFile dir = createChildDirectory(root, "dir");
    createChildData(dir, "somefile.txt");

    final TextFilePatch patch = create("dir/somefile.txt");

    MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    List<AbstractFilePatchInProgress<?>> filePatchInProgresses = iterator.execute(Collections.singletonList(patch));

    assertEquals(1, filePatchInProgresses.size());
    assertEquals(root, filePatchInProgresses.get(0).getBase());
    assertEquals("dir/somefile.txt", filePatchInProgresses.get(0).getCurrentPath());
    assertEquals(0, filePatchInProgresses.get(0).getCurrentStrip());

    FileUtil.delete(new File(dir.getPath()));
  }

  static TextFilePatch create(String s) {
    TextFilePatch patch = new TextFilePatch(null);
    patch.setBeforeName(s);
    patch.setAfterName(s);
    return patch;
  }

  private static TextFilePatch createFileAddition(String filename) {
    TextFilePatch patch = create(filename);
    patch.addHunk(new PatchHunk(-1, -1, 1, 1));
    return patch;
  }

  private static TextFilePatch createFileDeletion(String filename) {
    TextFilePatch patch = create(filename);
    patch.addHunk(new PatchHunk(1, 1, -1, -1));
    return patch;
  }

  // 1. several files with different bases; one can be matched to 2 bases
  public void testDiffBases() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile b = createChildDirectory(a, "b");
    VirtualFile c = createChildDirectory(b, "c");
    createChildData(c, "f1.txt");
    createChildData(c, "f2.txt");
    createChildData(c, "f3.txt");
    createChildData(c, "f4.txt");
    createChildData(c, BINARY_FILENAME);

    VirtualFile e = createChildDirectory(root, "e");
    VirtualFile f = createChildDirectory(e, "b");
    VirtualFile g = createChildDirectory(f, "c");
    createChildData(g, "f1.txt");
    createChildData(g, "f2.txt");
    createChildData(g, "f3.txt");
    createChildData(g, "f4.txt");
    createChildData(g, BINARY_FILENAME);

    TextFilePatch patch1 = create("b/c/f1.txt");
    TextFilePatch patch2 = create("a/b/c/f2.txt");
    TextFilePatch patch3 = create("e/b/c/f3.txt");
    TextFilePatch patch4 = create("c/f4.txt");
    ShelvedBinaryFilePatch shelvedBinaryPatch = createShelvedBinarySimplePatch("c/" + BINARY_FILENAME);

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Arrays.asList(patch1, patch2, patch3, patch4, shelvedBinaryPatch));
    checkPath(result, "b/c/f1.txt", Arrays.asList(a, e), 0);
    checkPath(result, "a/b/c/f2.txt", Collections.singletonList(root), 0);
    checkPath(result, "e/b/c/f3.txt", Collections.singletonList(root), 0);
    checkPath(result, "c/f4.txt", Arrays.asList(b, f), 0);
    checkPath(result, "c/" + BINARY_FILENAME, Arrays.asList(b, f), 0);
  }

  public void testBestBinaryVariant() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);
    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile b = createChildDirectory(a, "b");
    VirtualFile c = createChildDirectory(b, "c");
    VfsTestUtil.createFile(c, BINARY_FILENAME);
    VirtualFile cc = createChildDirectory(root, "c");
    VfsTestUtil.createFile(cc, BINARY_FILENAME);
    VirtualFile e = createChildDirectory(root, "e");
    VirtualFile f = createChildDirectory(e, "b");
    VirtualFile g = createChildDirectory(f, "c");
    VfsTestUtil.createFile(g, BINARY_FILENAME);

    String cBinary = "c/" + BINARY_FILENAME;

    ShelvedBinaryFilePatch shelvedBinaryPatch = createShelvedBinarySimplePatch(cBinary);
    final MatchPatchPaths matchPatchPaths = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> resultProjectBase = matchPatchPaths.execute(Collections.singletonList(shelvedBinaryPatch));
    checkPath(resultProjectBase, cBinary, Arrays.asList(root, b, f), 0);
    assertEquals(resultProjectBase.get(0).getBase(), getOrCreateProjectBaseDir());
  }

  @NotNull
  private static ShelvedBinaryFilePatch createShelvedBinarySimplePatch(@NotNull String binaryFilename) {
    return new ShelvedBinaryFilePatch(new ShelvedBinaryFile(binaryFilename, binaryFilename, null));
  }

  // inspired by IDEA-109608
  public void testFileAdditionGoesIntoCorrectFolder() {
    String path = "platform/util/src/com/intellij/util/io/SomeNewFile.java";
    TextFilePatch patch = createFileAddition(path);
    checkSingleFileOperationAmongSimilarFolders(path, patch);
  }

  public void testFileDeletionFromCorrectFolder() {
    String path = "platform/util/src/com/intellij/util/io/G.java";
    TextFilePatch patch = createFileDeletion(path);
    checkSingleFileOperationAmongSimilarFolders(path, patch);
  }

  public void testFileModificationFromCorrectFolder() {
    String path = "platform/platform-impl/src/io/B.java";
    TextFilePatch patch = create(path);
    checkSingleFileOperationAmongSimilarFolders(path, patch);
  }

  private void checkSingleFileOperationAmongSimilarFolders(final String filePath, final TextFilePatch patch){
    final VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createFile(root, "platform/platform-impl/src/com/intellij/util/io/A.java");
    VfsTestUtil.createFile(root, "platform/platform-impl/src/io/B.java");
    VfsTestUtil.createFile(root, "platform/util-rt/src/com/intellij/util/io/C.java");
    VfsTestUtil.createFile(root, "platform/testFramework/src/com/intellij/util/io/D.java");
    VfsTestUtil.createFile(root, "platform/util/testSrc/com/intellij/openapi/util/io/E.java");
    VfsTestUtil.createFile(root, "platform/util/testSrc/com/intellij/util/io/F.java");
    VfsTestUtil.createFile(root, "platform/util/src/com/intellij/util/io/G.java");
    VfsTestUtil.createFile(root, "a/platform/util/src/com/intellij/util/io/H.java");
    VfsTestUtil.createFile(root, "platform/util/src/com/intellij/openapi/util/io/I.java");
    VfsTestUtil.createFile(root, "platform/util/completely/different/folder/J.java");

    MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    List<AbstractFilePatchInProgress<?>> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, filePath, Collections.singletonList(root), 0);
  }

  // inspired by IDEA-118644
  public void testFileAdditionToNonexistentSubfolder() {
    final VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createDir(root, "platform/editor-ui-ex/src/com/intellij/openapi/editor/colors");
    VfsTestUtil.createDir(root, "plugins/properties/src/com/intellij/openapi/options/colors");
    VfsTestUtil.createDir(root, "platform/platform-api/src/com/intellij/openapi/editor/colors");
    VfsTestUtil.createDir(root, "java/java-impl/src/com/intellij/openapi/options/colors");
    VfsTestUtil.createDir(root, "platform/lang-impl/src/com/intellij/openapi/options/colors");
    VfsTestUtil.createDir(root, "platform/platform-tests/testSrc/com/intellij/openapi/editor");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    String path = "platform/platform-tests/testSrc/com/intellij/openapi/editor/colors/impl/A.java";
    TextFilePatch patch = create(path);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, path, Collections.singletonList(root), 0);
  }

  public void testFileAdditionGeneratedFromSuperRoot() {
    final VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createDir(root, "editor-ui-ex/src/com/intellij/openapi/editor/colors");
    VfsTestUtil.createDir(root, "platform-api/src/com/intellij/openapi/editor/colors");
    VfsTestUtil.createDir(root, "lang-impl/src/com/intellij/openapi/options/colors");
    VfsTestUtil.createDir(root, "platform-tests/testSrc/com/intellij/openapi/editor");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    String prefix = "community/platform/";
    String path = "platform-tests/testSrc/com/intellij/openapi/editor/colors/A.java";
    TextFilePatch patch = create(prefix + path);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, path, Collections.singletonList(root), StringUtil.split(prefix, "/").size());
  }

  public void testFileAdditionWithMultipleSimilarModules() {
    final VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createDir(root, "module-1/src/com/intellij/openapi/colors");
    VfsTestUtil.createDir(root, "module-2/src/com/intellij/openapi/editor");
    VfsTestUtil.createDir(root, "module-3/src/com/intellij/openapi/modules");
    VfsTestUtil.createDir(root, "platform-tests/testSrc/com/intellij/openapi/editor");

    TextFilePatch patch = createFileAddition("module-new/src/com/intellij/openapi/tests/SomeNewFile.java");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, "module-new/src/com/intellij/openapi/tests/SomeNewFile.java", Collections.singletonList(root), 0);
  }

  public void testFileModificationWithMultipleSimilarModules() {
    final VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createFile(root, "module-1/.idea/module.xml", "1\n2\n3\n4\n5\n6\n");
    VfsTestUtil.createFile(root, "module-2/.idea/module.xml", "1\n2\n3\n4\n5\n6\n");
    VfsTestUtil.createFile(root, "module-3/folder/.idea/module.xml", "1\n2\n3\n4\n5\n6\n");
    VfsTestUtil.createFile(root, ".idea/module.xml", "1\n2\n3\n4\n5\n6\n");

    TextFilePatch patch = create(".idea/module.xml");
    PatchHunk hunk = new PatchHunk(0, 3, 0, 3);
    hunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "1"));
    hunk.addLine(new PatchLine(PatchLine.Type.REMOVE, "2"));
    hunk.addLine(new PatchLine(PatchLine.Type.ADD, "New"));
    hunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, "3"));
    patch.addHunk(hunk);

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, ".idea/module.xml", Collections.singletonList(root), 0);
  }

  public void testFileModificationAboveProjectDir() {
    final VirtualFile root = getOrCreateProjectBaseDir();
    VirtualFile grandRoot = root.getParent().getParent();

    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createFile(grandRoot, "module-1/.idea/module.xml", "1\n2\n3\n4\n5\n6\n");
    VfsTestUtil.createFile(grandRoot, "module-2/.idea/module.xml", "1\n2\n3\n4\n5\n6\n");
    VfsTestUtil.createFile(root, "module-1/.idea/module.xml", "1\n2\n3\n4\n5\n6\n");
    VfsTestUtil.createFile(root, ".idea/module.xml", "1\n2\n3\n4\n5\n6\n");

    TextFilePatch patch1 = create("../../module-1/.idea/module.xml");
    TextFilePatch patch2 = create("../../module-2/.idea/module.xml");

    final MatchPatchPaths iterator1 = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result1 = iterator1.execute(Collections.singletonList(patch1), true);
    checkPath(result1, "module-1/.idea/module.xml", Collections.singletonList(grandRoot), 2);

    final MatchPatchPaths iterator2 = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result2 = iterator2.execute(Collections.singletonList(patch2), true);
    checkPath(result2, "module-2/.idea/module.xml", Collections.singletonList(grandRoot), 2);
  }

  public void testBinaryModificationWithMultipleSimilarModules() {
    final VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createFile(root, "module-1/.idea/module.bin");
    VfsTestUtil.createFile(root, "module-2/.idea/module.bin");
    VfsTestUtil.createFile(root, "module-3/folder/.idea/module.bin");
    VfsTestUtil.createFile(root, ".idea/module.bin");

    final ShelvedBinaryFilePatch patch = createShelvedBinarySimplePatch(".idea/module.bin");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, ".idea/module.bin", Collections.singletonList(root), 0);
  }

  private static void checkPath(List<AbstractFilePatchInProgress<?>> filePatchInProgresses, String path, List<VirtualFile> bases, int strip) {
    for (AbstractFilePatchInProgress<?> patch : filePatchInProgresses) {
      if (bases.contains(patch.getBase()) && path.equals(patch.getCurrentPath()) && (patch.getCurrentStrip() == strip)) {
        return;
      }
    }
    fail("Failed for (first base only shown) '" + bases.iterator().next().getPath() + " + " + path + " " + strip +
         "'; results: " + printPatches(filePatchInProgresses));
  }

  private static String printPatches(@NotNull List<AbstractFilePatchInProgress<?>> filePatchInProgresses) {
    StringBuilder sb = new StringBuilder();
    for (AbstractFilePatchInProgress<?> patch : filePatchInProgresses) {
      sb.append("\n").append(patch.getBase().getPath()).append(" + ").append(patch.getCurrentPath()).
        append(' ').append(patch.getCurrentStrip());
    }
    return sb.toString();
  }

  // 2. files can be for 1 dir and 1 strip distance
  public void testOneBaseAndStrip() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile b = createChildDirectory(a, "b");
    VirtualFile c = createChildDirectory(b, "c");
    createChildData(c, "f1.txt");
    createChildData(c, "f2.txt");
    createChildData(c, "f3.txt");
    createChildData(c, "f4.txt");
    createChildData(c, BINARY_FILENAME);

    TextFilePatch patch1 = create("t/b/c/f1.txt");
    TextFilePatch patch2 = create("t/b/c/f2.txt");
    TextFilePatch patch3 = create("t/b/c/f3.txt");
    TextFilePatch patch4 = create("t/b/c/f4.txt");
    ShelvedBinaryFilePatch shelvedBinaryPatch = createShelvedBinarySimplePatch("t/b/c/" + BINARY_FILENAME);

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Arrays.asList(patch1, patch2, patch3, patch4, shelvedBinaryPatch));

    checkPath(result, "b/c/f1.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/f2.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/f3.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/f4.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/" + BINARY_FILENAME, Collections.singletonList(a), 1);
  }

  // 3. files can be with 2 base dirs and 1-one distance, 2-different distances
  public void testOneBaseAndDifferentStrips() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile b = createChildDirectory(a, "b");
    VirtualFile c = createChildDirectory(b, "c");
    createChildData(c, "f1.txt");
    createChildData(c, "f2.txt");
    createChildData(c, "f3.txt");
    createChildData(c, "f4.txt");

    VirtualFile e = createChildDirectory(root, "e");
    VirtualFile h1 = createChildDirectory(e, "h1");
    VirtualFile a1 = createChildDirectory(e, "a1");

    VirtualFile f = createChildDirectory(a1, "b1");
    VirtualFile g = createChildDirectory(f, "c");
    createChildData(g, "f1.txt");
    createChildData(g, "f2.txt");
    createChildData(g, "f3.txt");
    createChildData(g, "f4.txt");

    VirtualFile c2 = createChildDirectory(h1, "c");
    createChildData(c2, "f2.txt");

    final TextFilePatch patch1 = create("a1/b1/c/f1.txt");
    final TextFilePatch patch2 = create("h1/c/f2.txt");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Arrays.asList(patch1, patch2));
    checkPath(result, "a1/b1/c/f1.txt", Collections.singletonList(e), 0);
    checkPath(result, "h1/c/f2.txt", Collections.singletonList(e), 0);
  }

  public void testPreviousFirstVariantAlsoMatches() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile b = createChildDirectory(a, "b");
    VirtualFile c = createChildDirectory(b, "c");
    createChildData(c, "f1.txt");
    createChildData(c, "f2.txt");
    createChildData(c, "f3.txt");
    createChildData(c, "f4.txt");
    createChildData(c, BINARY_FILENAME);

    final TextFilePatch patch1 = create("a1/b1/c/f1.txt");
    final TextFilePatch patch2 = create("h1/c/f2.txt");
    final ShelvedBinaryFilePatch shelvedBinaryPatch = createShelvedBinarySimplePatch("a1/b1/c/" + BINARY_FILENAME);

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Arrays.asList(patch1, patch2, shelvedBinaryPatch));
    checkPath(result, "c/f1.txt", Collections.singletonList(b), 2);
    checkPath(result, "c/f2.txt", Collections.singletonList(b), 1);
    checkPath(result, "c/" + BINARY_FILENAME, Collections.singletonList(b), 2);
  }

  public void testDefaultStrategyWorks() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile b = createChildDirectory(a, "b");
    VirtualFile c = createChildDirectory(b, "c");
    createChildData(c, "f1.txt");
    createChildData(c, "f2.txt");
    createChildData(c, "f3.txt");
    createChildData(c, "f4.txt");

    TextFilePatch patch1 = create("a1/b1/c/f1.txt");
    TextFilePatch patch2 = create("h1/cccc/f2.txt");
    TextFilePatch patch3 = create("b/c/f3.txt");
    TextFilePatch patch4 = create("b/ccc/f4.txt");
    TextFilePatch patch5 = create("h1/c/f10.txt");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(Arrays.asList(patch1, patch2, patch3, patch4, patch5));
    checkPath(result, "c/f1.txt", Collections.singletonList(b), 2);
    checkPath(result, "f2.txt", Collections.singletonList(c), 2);
    checkPath(result, "b/c/f3.txt", Collections.singletonList(a), 0);
    checkPath(result, "f4.txt", Collections.singletonList(c), 2);
    checkPath(result, "h1/c/f10.txt", Collections.singletonList(root), 0);
  }

  public void testExactWins() {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile mod1 = createChildDirectory(a, "mod1");
    VirtualFile mod2 = createChildDirectory(a, "mod2");
    VirtualFile b1 = createChildDirectory(mod1, "b");
    VirtualFile b2 = createChildDirectory(mod2, "b");
    VirtualFile c1 = createChildDirectory(b1, "c");
    VirtualFile c2 = createChildDirectory(b2, "c");

    createChildData(c1, "f1.txt");
    createChildData(c2, "f1.txt");
    createChildData(c2, "f10.txt");
    createChildData(c2, "f19.txt");
    createChildData(c2, "f18.txt");
    createChildData(c1, BINARY_FILENAME);
    createChildData(c2, BINARY_FILENAME);

    TextFilePatch patch1 = create("mod1/b/c/f1.txt");
    TextFilePatch patch2 = create("mod2/b/c/f1.txt");
    TextFilePatch patch3 = create("mod26/b4/c3/f188.txt");

    ShelvedBinaryFilePatch shelvedBinaryPatch1 = createShelvedBinarySimplePatch("mod1/b/c/" + BINARY_FILENAME);
    ShelvedBinaryFilePatch shelvedBinaryPatch2 = createShelvedBinarySimplePatch("mod2/b/c/" + BINARY_FILENAME);

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result =
      iterator.execute(Arrays.asList(patch1, patch2, patch3, shelvedBinaryPatch1, shelvedBinaryPatch2));
    checkPath(result, "mod1/b/c/f1.txt", Collections.singletonList(a), 0);
    checkPath(result, "mod2/b/c/f1.txt", Collections.singletonList(a), 0);
    checkPath(result, "mod26/b4/c3/f188.txt", Collections.singletonList(root), 0);
    checkPath(result, "mod1/b/c/" + BINARY_FILENAME, Collections.singletonList(a), 0);
    checkPath(result, "mod2/b/c/" + BINARY_FILENAME, Collections.singletonList(a), 0);
  }

  public void testFindByContext() throws Exception {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    VirtualFile a = createChildDirectory(root, "a");
    VirtualFile mod1 = createChildDirectory(a, "mod1");
    VirtualFile mod2 = createChildDirectory(a, "mod2");
    VirtualFile b1 = createChildDirectory(mod1, "b");
    VirtualFile b2 = createChildDirectory(mod2, "b");
    VirtualFile c1 = createChildDirectory(b1, "coupleFiles");
    VirtualFile c2 = createChildDirectory(b2, "coupleFiles");

    VirtualFile f11 = createChildData(c1, "file1.txt");
    VirtualFile f12 = createChildData(c2, "file1.txt");
    setFileText(f11, """
      Health care and education, in my view, are next up for fundamental software-based transformation.
      My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.
      We believe both of these industries, which historically have been highly resistant to entrepreneurial change,
      are primed for tipping by great new software-centric entrepreneurs.

      Even national defense is increasingly software-based.
      The modern combat soldier is embedded in a web of software that provides intelligence, communications,
      logistics and weapons guidance.
      Software-powered drones launch airstrikes without putting human pilots at risk.
      Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.
      555""");
    setFileText(f12, """
      Health care and education, in my view, are next up for fundamental software-based transformation.
      My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.
      We believe both of these industries, which historically have been highly resistant to entrepreneurial change,
      are primed for tipping by great new software-centric entrepreneurs.

      Even national defense is increasingly software-based.
      The modern combat soldier is embedded in a web of software that provides intelligence, communications,
      logistics and weapons guidance.
      Software-powered drones launch airstrikes without putting human pilots at risk.
      Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.

      Companies in every industry need to assume that a software revolution is coming.
      This includes even industries that are software-based today.
      Great incumbent software companies like Oracle and Microsoft are increasingly threatened with irrelevance
      by new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).

      In some industries, particularly those with a heavy real-world component such as oil and gas,
      the software revolution is primarily an opportunity for incumbents.
      But in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups
      that invade existing industries with impunity.
      Over the next 10 years, the battles between incumbents and software-powered insurgents will be epic.
      Joseph Schumpeter, the economist who coined the term "creative destruction," would be proud.""");

    final List<TextFilePatch> patches = new PatchReader("""
                                                          Index: coupleFiles/file1.txt
                                                          IDEA additional info:
                                                          Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
                                                          <+>UTF-8
                                                          Subsystem: com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP
                                                          <+>Health care and education, in my view, are next up for fundamental software-based transformation.\\nMy venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\\nWe believe both of these industries, which historically have been highly resistant to entrepreneurial change,\\nare primed for tipping by great new software-centric entrepreneurs.\\n\\nEven national defense is increasingly software-based.\\nThe modern combat soldier is embedded in a web of software that provides intelligence, communications,\\nlogistics and weapons guidance.\\nSoftware-powered drones launch airstrikes without putting human pilots at risk.\\nIntelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\\n\\nCompanies in every industry need to assume that a software revolution is coming.\\nThis includes even industries that are software-based today.\\nGreat incumbent software companies like Oracle and Microsoft are increasingly threatened with irrelevance\\nby new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).\\n\\nIn some industries, particularly those with a heavy real-world component such as oil and gas,\\nthe software revolution is primarily an opportunity for incumbents.\\nBut in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups\\nthat invade existing industries with impunity.\\nOver the next 10 years, the battles between incumbents and software-powered insurgents will be epic.\\nJoseph Schumpeter, the economist who coined the term \\"creative destruction,\\" would be proud.
                                                          ===================================================================
                                                          --- coupleFiles/file1.txt\t(date 1351241865000)
                                                          +++ coupleFiles/file1.txt\t(revision )
                                                          @@ -15,7 +15,7 @@
                                                           by new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).
                                                          \s
                                                           In some industries, particularly those with a heavy real-world component such as oil and gas,
                                                          -the software revolution is primarily an opportunity for incumbents.
                                                          +the software revolution is primarily an opportunity for incumbents.Unique
                                                           But in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups
                                                           that invade existing industries with impunity.
                                                           Over the next 10 years, the battles between incumbents and software-powered insurgents will be epic.
                                                          \\ No newline at end of file
                                                          """).readTextPatches();

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(patches);
    checkPath(result, "coupleFiles/file1.txt", Collections.singletonList(b2), 0);
  }

  public void testFindByContext2() throws Exception {
    final VirtualFile root = getOrCreateProjectBaseDir();

    PsiTestUtil.addContentRoot(myModule, root);

    final VirtualFile a = createChildDirectory(root, "a");
    final VirtualFile mod1 = createChildDirectory(a, "mod1");
    final VirtualFile mod2 = createChildDirectory(a, "mod2");
    final VirtualFile b1 = createChildDirectory(mod1, "b");
    final VirtualFile b2 = createChildDirectory(mod2, "b");
    final VirtualFile c1 = createChildDirectory(b1, "coupleFiles");
    final VirtualFile c2 = createChildDirectory(b2, "coupleFiles");

    final VirtualFile f11 = createChildData(c1, "file1.txt");
    final VirtualFile f12 = createChildData(c2, "file1.txt");
    setFileText(f11, """
      Health care and education, in my view, are next up for fundamental software-based transformation.
      My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.
      We believe both of these industries, which historically have been highly resistant to entrepreneurial change,
      are primed for tipping by great new software-centric entrepreneurs.

      Even national defense is increasingly software-based.
      The modern combat soldier is embedded in a web of software that provides intelligence, communications,
      logistics and weapons guidance.
      Software-powered drones launch airstrikes without putting human pilots at risk.
      Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.
      555""");
    setFileText(f12, """
      Health care and education, in my view, are next up for fundamental software-based transformation.
      My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.
      We believe both of these industries, which historically have been highly resistant to entrepreneurial change,
      are primed for tipping by great new software-centric entrepreneurs.

      Even national defense is increasingly software-based.
      The modern combat soldier is embedded in a web of software that provides intelligence, communications,
      logistics and weapons guidance.
      Software-powered drones launch airstrikes without putting human pilots at risk.
      Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.

      Companies in every industry need to assume that a software revolution is coming.
      This includes even industries that are software-based today.
      Great incumbent software companies like Oracle and Microsoft are increasingly threatened with irrelevance
      by new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).

      In some industries, particularly those with a heavy real-world component such as oil and gas,
      the software revolution is primarily an opportunity for incumbents.
      But in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups
      that invade existing industries with impunity.
      Over the next 10 years, the battles between incumbents and software-powered insurgents will be epic.
      Joseph Schumpeter, the economist who coined the term "creative destruction," would be proud.""");

    final List<TextFilePatch> patches = new PatchReader("""
                                                          Index: coupleFiles/file1.txt
                                                          IDEA additional info:
                                                          Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
                                                          <+>UTF-8
                                                          Subsystem: com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP
                                                          <+>Health care and education, in my view, are next up for fundamental software-based transformation.\\nMy venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\\nWe believe both of these industries, which historically have been highly resistant to entrepreneurial change,\\nare primed for tipping by great new software-centric entrepreneurs.\\n\\nEven national defense is increasingly software-based.\\nThe modern combat soldier is embedded in a web of software that provides intelligence, communications,\\nlogistics and weapons guidance.\\nSoftware-powered drones launch airstrikes without putting human pilots at risk.\\nIntelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\\n555
                                                          ===================================================================
                                                          --- coupleFiles/file1.txt\t(date 1351242049000)
                                                          +++ coupleFiles/file1.txt\t(revision )
                                                          @@ -8,4 +8,4 @@
                                                           logistics and weapons guidance.
                                                           Software-powered drones launch airstrikes without putting human pilots at risk.
                                                           Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.
                                                          -555
                                                          \\ No newline at end of file
                                                          +555 ->
                                                          \\ No newline at end of file
                                                          """).readTextPatches();

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress<?>> result = iterator.execute(patches);
    checkPath(result, "coupleFiles/file1.txt", Collections.singletonList(b1), 0);
  }

  public void testFindProjectDirBasedOrAccordingContext() throws Exception {
    VirtualFile root = getOrCreateProjectBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);

    createChildData(root, "fff1.txt");
    VirtualFile subdir = createChildDirectory(root, "subdir");
    VirtualFile wrongVariant = createChildData(subdir, "fff1.txt");

    setFileText(wrongVariant, """
      aaaa
      bbbb
      dddd
      eeee""");

    List<TextFilePatch> patches = new PatchReader("""
                                                    Index: fff1.txt
                                                    IDEA additional info:
                                                    Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
                                                    <+>UTF-8
                                                    ===================================================================
                                                    --- fff1.txt\t(date 1459006145000)
                                                    +++ fff1.txt\t(revision )
                                                    @@ -1,4 +1,5 @@
                                                     aaaa
                                                     bbbb
                                                    +cccc
                                                     dddd
                                                     eeee
                                                    \\ No newline at end of file
                                                    """).readTextPatches();

    List<AbstractFilePatchInProgress<?>> result = new MatchPatchPaths(myProject).execute(patches, true);
    checkPath(result, "fff1.txt", Collections.singletonList(root), 0);
    result = new MatchPatchPaths(myProject).execute(patches);
    checkPath(result, "fff1.txt", Collections.singletonList(subdir), 0);
  }
}
