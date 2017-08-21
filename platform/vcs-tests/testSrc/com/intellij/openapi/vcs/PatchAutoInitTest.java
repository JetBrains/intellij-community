/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@PlatformTestCase.WrapInCommand
public class PatchAutoInitTest extends PlatformTestCase {
  private static final String BINARY_FILENAME = "binary.png";

  public void testSimple() {
    final VirtualFile root = myProject.getBaseDir();
    final VirtualFile dir = createChildDirectory(root, "dir");
    createChildData(dir, "somefile.txt");

    final TextFilePatch patch = create("dir/somefile.txt");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress> filePatchInProgresses = iterator.execute(Collections.singletonList(patch));

    assertEquals(1, filePatchInProgresses.size());
    assertEquals(root, filePatchInProgresses.get(0).getBase());
    assertEquals("dir/somefile.txt", filePatchInProgresses.get(0).getCurrentPath());
    assertEquals(0, filePatchInProgresses.get(0).getCurrentStrip());

    FileUtil.delete(new File(dir.getPath()));
  }

  static TextFilePatch create(String s) {
    final TextFilePatch patch = new TextFilePatch(null);
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
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> result = iterator.execute(Arrays.asList(patch1, patch2, patch3, patch4, shelvedBinaryPatch));
    checkPath(result, "b/c/f1.txt", Arrays.asList(a, e), 0);
    checkPath(result, "a/b/c/f2.txt", Collections.singletonList(root), 0);
    checkPath(result, "e/b/c/f3.txt", Collections.singletonList(root), 0);
    checkPath(result, "c/f4.txt", Arrays.asList(b, f), 0);
    checkPath(result, "c/" + BINARY_FILENAME, Arrays.asList(b, f), 0);
  }

  public void testBestBinaryVariant() {
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> resultProjectBase = matchPatchPaths.execute(Collections.singletonList(shelvedBinaryPatch));
    checkPath(resultProjectBase, cBinary, Arrays.asList(root, b, f), 0);
    assertEquals(resultProjectBase.get(0).getBase(), myProject.getBaseDir());
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
    final VirtualFile root = myProject.getBaseDir();
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

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, filePath, Collections.singletonList(root), 0);
  }

  // inspired by IDEA-118644
  public void testFileAdditionToNonexistentSubfolder() {
    final VirtualFile root = myProject.getBaseDir();
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
    final List<AbstractFilePatchInProgress> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, path, Collections.singletonList(root), 0);
  }

  public void testFileAdditionGeneratedFromSuperRoot() {
    final VirtualFile root = myProject.getBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);
    VfsTestUtil.createDir(root, "editor-ui-ex/src/com/intellij/openapi/editor/colors");
    VfsTestUtil.createDir(root, "platform-api/src/com/intellij/openapi/editor/colors");
    VfsTestUtil.createDir(root, "lang-impl/src/com/intellij/openapi/options/colors");
    VfsTestUtil.createDir(root, "platform-tests/testSrc/com/intellij/openapi/editor");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    String prefix = "community/platform/";
    String path = "platform-tests/testSrc/com/intellij/openapi/editor/colors/A.java";
    TextFilePatch patch = create(prefix + path);
    final List<AbstractFilePatchInProgress> result = iterator.execute(Collections.singletonList(patch));
    checkPath(result, path, Collections.singletonList(root), StringUtil.split(prefix, "/").size());
  }

  private static void checkPath(List<AbstractFilePatchInProgress> filePatchInProgresses, String path, List<VirtualFile> bases, int strip) {
    for (AbstractFilePatchInProgress patch : filePatchInProgresses) {
      if (bases.contains(patch.getBase()) && path.equals(patch.getCurrentPath()) && (patch.getCurrentStrip() == strip)) {
        return;
      }
    }
    assertTrue("Failed for (first base only shown) '" + bases.iterator().next().getPath() + " + " + path + " " + strip +
               "'; results: " + printPatches(filePatchInProgresses), false);
  }

  private static String printPatches(final List<AbstractFilePatchInProgress> filePatchInProgresses) {
    final StringBuilder sb = new StringBuilder();
    for (AbstractFilePatchInProgress patch : filePatchInProgresses) {
      sb.append("\n").append(patch.getBase().getPath()).append(" + ").append(patch.getCurrentPath()).
        append(' ').append(patch.getCurrentStrip());
    }
    return sb.toString();
  }

  // 2. files can be for 1 dir and 1 strip distance
  public void testOneBaseAndStrip() {
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> result = iterator.execute(Arrays.asList(patch1, patch2, patch3, patch4, shelvedBinaryPatch));

    checkPath(result, "b/c/f1.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/f2.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/f3.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/f4.txt", Collections.singletonList(a), 1);
    checkPath(result, "b/c/" + BINARY_FILENAME, Collections.singletonList(a), 1);
  }

  // 3. files can be with 2 base dirs and 1-one distance, 2-different distances
  public void testOneBaseAndDifferentStrips() {
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> result = iterator.execute(Arrays.asList(patch1, patch2));
    checkPath(result, "a1/b1/c/f1.txt", Collections.singletonList(e), 0);
    checkPath(result, "h1/c/f2.txt", Collections.singletonList(e), 0);
  }

  public void testPreviousFirstVariantAlsoMatches() {
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> result = iterator.execute(Arrays.asList(patch1, patch2, shelvedBinaryPatch));
    checkPath(result, "c/f1.txt", Collections.singletonList(b), 2);
    checkPath(result, "c/f2.txt", Collections.singletonList(b), 1);
    checkPath(result, "c/" + BINARY_FILENAME, Collections.singletonList(b), 2);
  }

  public void testDefaultStrategyWorks() {
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> result = iterator.execute(Arrays.asList(patch1, patch2, patch3, patch4, patch5));
    checkPath(result, "c/f1.txt", Collections.singletonList(b), 2);
    checkPath(result, "f2.txt", Collections.singletonList(c), 2);
    checkPath(result, "b/c/f3.txt", Collections.singletonList(a), 0);
    checkPath(result, "f4.txt", Collections.singletonList(c), 2);
    checkPath(result, "h1/c/f10.txt", Collections.singletonList(root), 0);
  }

  public void testExactWins() {
    final VirtualFile root = myProject.getBaseDir();

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
    final List<AbstractFilePatchInProgress> result =
      iterator.execute(Arrays.asList(patch1, patch2, patch3, shelvedBinaryPatch1, shelvedBinaryPatch2));
    checkPath(result, "mod1/b/c/f1.txt", Collections.singletonList(a), 0);
    checkPath(result, "mod2/b/c/f1.txt", Collections.singletonList(a), 0);
    checkPath(result, "mod26/b4/c3/f188.txt", Collections.singletonList(root), 0);
    checkPath(result, "mod1/b/c/" + BINARY_FILENAME, Collections.singletonList(a), 0);
    checkPath(result, "mod2/b/c/" + BINARY_FILENAME, Collections.singletonList(a), 0);
  }

  public void testFindByContext() throws Exception {
    final VirtualFile root = myProject.getBaseDir();

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
    setFileText(f11, "Health care and education, in my view, are next up for fundamental software-based transformation.\n" +
                     "My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\n" +
                     "We believe both of these industries, which historically have been highly resistant to entrepreneurial change,\n" +
                     "are primed for tipping by great new software-centric entrepreneurs.\n" +
                     "\n" +
                     "Even national defense is increasingly software-based.\n" +
                     "The modern combat soldier is embedded in a web of software that provides intelligence, communications,\n" +
                     "logistics and weapons guidance.\n" +
                     "Software-powered drones launch airstrikes without putting human pilots at risk.\n" +
                     "Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\n" +
                     "555");
    setFileText(f12, "Health care and education, in my view, are next up for fundamental software-based transformation.\n" +
                     "My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\n" +
                     "We believe both of these industries, which historically have been highly resistant to entrepreneurial change,\n" +
                     "are primed for tipping by great new software-centric entrepreneurs.\n" +
                     "\n" +
                     "Even national defense is increasingly software-based.\n" +
                     "The modern combat soldier is embedded in a web of software that provides intelligence, communications,\n" +
                     "logistics and weapons guidance.\n" +
                     "Software-powered drones launch airstrikes without putting human pilots at risk.\n" +
                     "Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\n" +
                     "\n" +
                     "Companies in every industry need to assume that a software revolution is coming.\n" +
                     "This includes even industries that are software-based today.\n" +
                     "Great incumbent software companies like Oracle and Microsoft are increasingly threatened with irrelevance\n" +
                     "by new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).\n" +
                     "\n" +
                     "In some industries, particularly those with a heavy real-world component such as oil and gas,\n" +
                     "the software revolution is primarily an opportunity for incumbents.\n" +
                     "But in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups\n" +
                     "that invade existing industries with impunity.\n" +
                     "Over the next 10 years, the battles between incumbents and software-powered insurgents will be epic.\n" +
                     "Joseph Schumpeter, the economist who coined the term \"creative destruction,\" would be proud.");

    final List<TextFilePatch> patches = new PatchReader("Index: coupleFiles/file1.txt\n" +
                                                        "IDEA additional info:\n" +
                                                        "Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
                                                        "<+>UTF-8\n" +
                                                        "Subsystem: com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP\n" +
                                                        "<+>Health care and education, in my view, are next up for fundamental software-based transformation.\\nMy venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\\nWe believe both of these industries, which historically have been highly resistant to entrepreneurial change,\\nare primed for tipping by great new software-centric entrepreneurs.\\n\\nEven national defense is increasingly software-based.\\nThe modern combat soldier is embedded in a web of software that provides intelligence, communications,\\nlogistics and weapons guidance.\\nSoftware-powered drones launch airstrikes without putting human pilots at risk.\\nIntelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\\n\\nCompanies in every industry need to assume that a software revolution is coming.\\nThis includes even industries that are software-based today.\\nGreat incumbent software companies like Oracle and Microsoft are increasingly threatened with irrelevance\\nby new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).\\n\\nIn some industries, particularly those with a heavy real-world component such as oil and gas,\\nthe software revolution is primarily an opportunity for incumbents.\\nBut in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups\\nthat invade existing industries with impunity.\\nOver the next 10 years, the battles between incumbents and software-powered insurgents will be epic.\\nJoseph Schumpeter, the economist who coined the term \\\"creative destruction,\\\" would be proud.\n" +
                                                        "===================================================================\n" +
                                                        "--- coupleFiles/file1.txt\t(date 1351241865000)\n" +
                                                        "+++ coupleFiles/file1.txt\t(revision )\n" +
                                                        "@@ -15,7 +15,7 @@\n" +
                                                        " by new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).\n" +
                                                        " \n" +
                                                        " In some industries, particularly those with a heavy real-world component such as oil and gas,\n" +
                                                        "-the software revolution is primarily an opportunity for incumbents.\n" +
                                                        "+the software revolution is primarily an opportunity for incumbents.Unique\n" +
                                                        " But in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups\n" +
                                                        " that invade existing industries with impunity.\n" +
                                                        " Over the next 10 years, the battles between incumbents and software-powered insurgents will be epic.\n" +
                                                        "\\ No newline at end of file\n").readTextPatches();

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress> result = iterator.execute(patches);
    checkPath(result, "coupleFiles/file1.txt", Collections.singletonList(b2), 0);
  }

  public void testFindByContext2() throws Exception {
    final VirtualFile root = myProject.getBaseDir();

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
    setFileText(f11, "Health care and education, in my view, are next up for fundamental software-based transformation.\n" +
                     "My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\n" +
                     "We believe both of these industries, which historically have been highly resistant to entrepreneurial change,\n" +
                     "are primed for tipping by great new software-centric entrepreneurs.\n" +
                     "\n" +
                     "Even national defense is increasingly software-based.\n" +
                     "The modern combat soldier is embedded in a web of software that provides intelligence, communications,\n" +
                     "logistics and weapons guidance.\n" +
                     "Software-powered drones launch airstrikes without putting human pilots at risk.\n" +
                     "Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\n" +
                     "555");
    setFileText(f12, "Health care and education, in my view, are next up for fundamental software-based transformation.\n" +
                     "My venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\n" +
                     "We believe both of these industries, which historically have been highly resistant to entrepreneurial change,\n" +
                     "are primed for tipping by great new software-centric entrepreneurs.\n" +
                     "\n" +
                     "Even national defense is increasingly software-based.\n" +
                     "The modern combat soldier is embedded in a web of software that provides intelligence, communications,\n" +
                     "logistics and weapons guidance.\n" +
                     "Software-powered drones launch airstrikes without putting human pilots at risk.\n" +
                     "Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\n" +
                     "\n" +
                     "Companies in every industry need to assume that a software revolution is coming.\n" +
                     "This includes even industries that are software-based today.\n" +
                     "Great incumbent software companies like Oracle and Microsoft are increasingly threatened with irrelevance\n" +
                     "by new software offerings like Salesforce.com and Android (especially in a world where Google owns a major handset maker).\n" +
                     "\n" +
                     "In some industries, particularly those with a heavy real-world component such as oil and gas,\n" +
                     "the software revolution is primarily an opportunity for incumbents.\n" +
                     "But in many industries, new software ideas will result in the rise of new Silicon Valley-style start-ups\n" +
                     "that invade existing industries with impunity.\n" +
                     "Over the next 10 years, the battles between incumbents and software-powered insurgents will be epic.\n" +
                     "Joseph Schumpeter, the economist who coined the term \"creative destruction,\" would be proud.");

    final List<TextFilePatch> patches = new PatchReader("Index: coupleFiles/file1.txt\n" +
                                                        "IDEA additional info:\n" +
                                                        "Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
                                                        "<+>UTF-8\n" +
                                                        "Subsystem: com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP\n" +
                                                        "<+>Health care and education, in my view, are next up for fundamental software-based transformation.\\nMy venture capital firm is backing aggressive start-ups in both of these gigantic and critical industries.\\nWe believe both of these industries, which historically have been highly resistant to entrepreneurial change,\\nare primed for tipping by great new software-centric entrepreneurs.\\n\\nEven national defense is increasingly software-based.\\nThe modern combat soldier is embedded in a web of software that provides intelligence, communications,\\nlogistics and weapons guidance.\\nSoftware-powered drones launch airstrikes without putting human pilots at risk.\\nIntelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\\n555\n" +
                                                        "===================================================================\n" +
                                                        "--- coupleFiles/file1.txt\t(date 1351242049000)\n" +
                                                        "+++ coupleFiles/file1.txt\t(revision )\n" +
                                                        "@@ -8,4 +8,4 @@\n" +
                                                        " logistics and weapons guidance.\n" +
                                                        " Software-powered drones launch airstrikes without putting human pilots at risk.\n" +
                                                        " Intelligence agencies do large-scale data mining with software to uncover and track potential terrorist plots.\n" +
                                                        "-555\n" +
                                                        "\\ No newline at end of file\n" +
                                                        "+555 ->\n" +
                                                        "\\ No newline at end of file\n").readTextPatches();

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress> result = iterator.execute(patches);
    checkPath(result, "coupleFiles/file1.txt", Collections.singletonList(b1), 0);
  }

  public void testFindProjectDirBasedOrAccordingContext() throws Exception {
    VirtualFile root = myProject.getBaseDir();
    PsiTestUtil.addContentRoot(myModule, root);

    createChildData(root, "fff1.txt");
    VirtualFile subdir = createChildDirectory(root, "subdir");
    VirtualFile wrongVariant = createChildData(subdir, "fff1.txt");

    setFileText(wrongVariant, "aaaa\n" +
                              "bbbb\n" +
                              "dddd\n" +
                              "eeee");

    List<TextFilePatch> patches = new PatchReader("Index: fff1.txt\n" +
                                                  "IDEA additional info:\n" +
                                                  "Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP\n" +
                                                  "<+>UTF-8\n" +
                                                  "===================================================================\n" +
                                                  "--- fff1.txt\t(date 1459006145000)\n" +
                                                  "+++ fff1.txt\t(revision )\n" +
                                                  "@@ -1,4 +1,5 @@\n" +
                                                  " aaaa\n" +
                                                  " bbbb\n" +
                                                  "+cccc\n" +
                                                  " dddd\n" +
                                                  " eeee\n" +
                                                  "\\ No newline at end of file\n").readTextPatches();

    List<AbstractFilePatchInProgress> result = new MatchPatchPaths(myProject).execute(patches, true);
    checkPath(result, "fff1.txt", Collections.singletonList(root), 0);
    result = new MatchPatchPaths(myProject).execute(patches);
    checkPath(result, "fff1.txt", Collections.singletonList(subdir), 0);
  }
}
