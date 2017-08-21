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
package com.intellij.testFramework.propertyBased;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import jetCheck.DataStructure;
import jetCheck.Generator;
import jetCheck.IntDistribution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class MadTestingUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.propertyBased.MadTestingUtil");
  
  public static void restrictChangesToDocument(Document document, Runnable r) {
    letSaveAllDocumentsPassIfAny();
    watchDocumentChanges(r::run, event -> {
      Document changed = event.getDocument();
      if (changed != document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(changed);
        if (file != null && file.isInLocalFileSystem()) {
          throw new AssertionError("Unexpected document change: " + changed);
        }
      }
    });
  }

  //for possible com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.saveAllDocumentsLater
  private static void letSaveAllDocumentsPassIfAny() {
    UIUtil.dispatchAllInvocationEvents();
  }

  public static void prohibitDocumentChanges(Runnable r) {
    letSaveAllDocumentsPassIfAny();
    watchDocumentChanges(r::run, event -> {
      Document changed = event.getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(changed);
      if (file != null && file.isInLocalFileSystem()) {
        throw new AssertionError("Unexpected document change: " + changed);
      }
    });
  }

  private static <E extends Throwable> void watchDocumentChanges(ThrowableRunnable<E> r, Consumer<DocumentEvent> eventHandler) throws E {
    Disposable disposable = Disposer.newDisposable();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent event) {
        eventHandler.accept(event);
      }
    }, disposable);
    try {
      r.run();
    } finally {
      Disposer.dispose(disposable);
    }
  }

  public static void changeAndRevert(Project project, Runnable r) {
    Label label = LocalHistory.getInstance().putUserLabel(project, "changeAndRevert");
    boolean failed = false;
    try {
      r.run();
    }
    catch (Throwable e) {
      failed = true;
      throw e;
    }
    finally {
      restoreEverything(label, failed, project);
    }
  }

  private static void restoreEverything(Label label, boolean failed, Project project) {
    try {
      WriteAction.run(() -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        new RunAll(
          () -> PostprocessReformattingAspect.getInstance(project).doPostponedFormatting(),
          () -> FileEditorManagerEx.getInstanceEx(project).closeAllFiles(),
          () -> FileDocumentManager.getInstance().saveAllDocuments(),
          () -> revertVfs(label, project),
          () -> documentManager.commitAllDocuments(),
          () -> UsefulTestCase.assertEmpty(documentManager.getUncommittedDocuments()),
          () -> UsefulTestCase.assertEmpty(FileDocumentManager.getInstance().getUnsavedDocuments())
        ).run();
      });
    }
    catch (Throwable e) {
      if (failed) {
        LOG.info("Exceptions while restoring state", e);
      } else {
        throw e;
      }
    }
  }

  private static void revertVfs(Label label, Project project) throws LocalHistoryException {
    watchDocumentChanges(() -> label.revert(project, project.getBaseDir()),
                               __ -> {
                                 PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                                 if (documentManager.getUncommittedDocuments().length > 3) {
                                   documentManager.commitAllDocuments();
                                 }
                               });
  }

  public static void enableAllInspections(Project project, Disposable disposable) {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    InspectionProfileImpl profile = new InspectionProfileImpl("allEnabled");
    profile.enableAllTools(project);
    
    ProjectInspectionProfileManager manager = (ProjectInspectionProfileManager)InspectionProjectProfileManager.getInstance(project);
    manager.addProfile(profile);
    InspectionProfileImpl prev = manager.getCurrentProfile();
    manager.setCurrentProfile(profile);
    Disposer.register(disposable, () -> {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      manager.setCurrentProfile(prev);
      manager.deleteProfile(profile);
    });
  }

  /**
   * Finds files under {@code rootPath} (e.g. test data root) satisfying {@code fileFilter condition} (e.g. correct extension) and uses {@code actions} to generate actions those files (e.g. invoke completion/intentions or random editing).
   * Almost: the files with same paths and contents are created inside the test project, then the actions are executed on them.
   * Note that the test project contains only one file at each moment, so it's best to test actions that don't require much environment. 
   * @return
   */
  @NotNull
  public static Generator<FileWithActions> actionsOnFileContents(CodeInsightTestFixture fixture, String rootPath,
                                                                 FileFilter fileFilter,
                                                                 Function<PsiFile, Generator<? extends MadTestingAction>> actions) {
    FileFilter interestingIdeaFiles = child -> {
      String name = child.getName();
      if (name.startsWith(".")) return false;

      if (child.isDirectory()) {
        return shouldGoInsiderDir(name);
      }
      return !FileTypeManager.getInstance().getFileTypeByFileName(name).isBinary() &&
             fileFilter.accept(child) &&
             child.length() < 500_000;
    };
    Generator<File> randomFiles = Generator.from(new FileGenerator(new File(rootPath), interestingIdeaFiles))
      .suchThat(new Predicate<File>() {
        @Override
        public boolean test(File file) {
          return file != null;
        }

        @Override
        public String toString() {
          return "can find a file under " + rootPath + " satisfying given filters";
        }
      })
      .noShrink();
    return randomFiles.flatMap(ioFile -> {
      VirtualFile vFile = copyFileToProject(ioFile, fixture, rootPath);
      PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
      PsiFile file = PsiManager.getInstance(fixture.getProject()).findFile(vFile);
      if (file instanceof PsiBinaryFile || file instanceof PsiPlainTextFile) {
        // no operations, but the just created file needs to be deleted (in FileWithActions#runActions)
        // todo a side-effect-free generator
        return Generator.constant(new FileWithActions(file, Collections.emptyList()));
      }
      return Generator.nonEmptyLists(actions.apply(file)).map(a -> new FileWithActions(file, a));
    });
  }

  private static boolean shouldGoInsiderDir(@NotNull String name) {
    return !name.equals("gen") && // https://youtrack.jetbrains.com/issue/IDEA-175404
           !name.equals("reports") && // no idea what this is
           !name.equals("android") && // no 'android' repo on agents in some builds
           !containsBinariesOnly(name) &&
           !name.endsWith("system") && !name.endsWith("config"); // temporary stuff from tests or debug IDE
  }

  private static boolean containsBinariesOnly(@NotNull String name) {
    return name.equals("jdk") ||
           name.equals("jre") ||
           name.equals("lib") ||
           name.equals("bin") ||
           name.equals("out");
  }

  @NotNull
  private static VirtualFile copyFileToProject(File ioFile, CodeInsightTestFixture fixture, String rootPath) {
    try {
      String path = FileUtil.getRelativePath(FileUtil.toCanonicalPath(rootPath),  FileUtil.toSystemIndependentName(ioFile.getPath()), '/');
      assert path != null;
      VirtualFile existing = fixture.getTempDirFixture().getFile(path);
      if (existing != null) {
        WriteAction.run(() -> existing.delete(fixture));
      }

      return fixture.addFileToProject(path, FileUtil.loadFile(ioFile)).getVirtualFile();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Generates actions checking that incremental reparse produces the same PSI as full reparse. This check makes sense
   * in languages employing {@link com.intellij.psi.tree.ILazyParseableElementTypeBase}.
   */
  @NotNull
  public static Generator<MadTestingAction> randomEditsWithReparseChecks(PsiFile file) {
    return Generator.anyOf(DeleteRange.psiRangeDeletions(file),
                           Generator.constant(new CheckPsiTextConsistency(file)),
                           InsertString.asciiInsertions(file));
  }

  public static boolean isAfterError(PsiFile file, int offset) {
    PsiElement leaf = file.findElementAt(offset);
    Set<Integer> errorOffsets = SyntaxTraverser.psiTraverser(file)
      .filter(PsiErrorElement.class)
      .map(PsiTreeUtil::nextVisibleLeaf)
      .filter(Condition.NOT_NULL)
      .map(e -> e.getTextRange().getStartOffset())
      .toSet();
    return !errorOffsets.isEmpty() &&
           SyntaxTraverser.psiApi().parents(leaf).find(e -> errorOffsets.contains(e.getTextRange().getStartOffset())) != null;
  }

  private static class FileGenerator implements Function<DataStructure, File> {
    private final File myRoot;
    private final FileFilter myFilter;

    public FileGenerator(File root, FileFilter filter) {
      myRoot = root;
      myFilter = filter;
    }

    @Override
    public File apply(DataStructure data) {
      return generateRandomFile(data, myRoot, new HashSet<>());
    }

    @Nullable
    private File generateRandomFile(DataStructure data, File file, Set<File> exhausted) {
      while (true) {
        File[] children = file.listFiles(f -> !exhausted.contains(f) && myFilter.accept(f));
        if (children == null) {
          return file;
        }
        if (children.length == 0) {
          exhausted.add(file);
          return null;
        }

        List<File> toChoose = preferDirs(data, children);
        Collections.sort(toChoose);
        int index = data.drawInt(IntDistribution.uniform(0, toChoose.size() - 1));
        File generated = generateRandomFile(data, toChoose.get(index), exhausted);
        if (generated != null) {
          return generated;
        }
      }
    }

    private static List<File> preferDirs(DataStructure data, File[] children) {
      List<File> files = new ArrayList<>();
      List<File> dirs = new ArrayList<>();
      for (File child : children) {
        (child.isDirectory() ? dirs : files).add(child);
      }

      if (files.isEmpty() || dirs.isEmpty()) {
        return Arrays.asList(children);
      }

      int ratio = Math.max(100, dirs.size() / files.size());
      return data.drawInt() % ratio != 0 ? dirs : files;
    }
  }
}
