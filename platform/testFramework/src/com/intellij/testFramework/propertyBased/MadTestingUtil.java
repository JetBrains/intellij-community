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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.SoftFactoryMap;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.DataStructure;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author peter
 */
public class MadTestingUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.propertyBased.MadTestingUtil");
  private static final boolean USE_ROULETTE_WHEEL = true;

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
    ToolsImpl goodCodeIsRed = profile.getToolsOrNull("HighlightVisitorInternal", project);
    if (goodCodeIsRed != null) {
      // This inspection has error-level by default and highlights the first token from erroneous range
      // which is not very stable and also masks other warning-level inspections available on the same token.
      goodCodeIsRed.setEnabled(false);
    }

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

  private static Generator<File> randomFiles(String rootPath, FileFilter fileFilter) {
    return randomFiles(rootPath, fileFilter, USE_ROULETTE_WHEEL);
  }

  private static Generator<File> randomFiles(String rootPath,
                                             FileFilter fileFilter,
                                             boolean useRouletteWheel) {
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
    File root = new File(rootPath);
    Function<DataStructure, File> generator =
      useRouletteWheel ? new RouletteWheelFileGenerator(root, interestingIdeaFiles) : new FileGenerator(root, interestingIdeaFiles);
    return Generator.from(generator)
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
  }

  /**
   * Finds files under {@code rootPath} (e.g. test data root) satisfying {@code fileFilter condition} (e.g. correct extension) and uses {@code actions} to generate actions those files (e.g. invoke completion/intentions or random editing).
   * Almost: the files with same paths and contents are created inside the test project, then the actions are executed on them.
   * Note that the test project contains only one file at each moment, so it's best to test actions that don't require much environment. 
   * @return
   */
  @NotNull
  public static Supplier<MadTestingAction> actionsOnFileContents(CodeInsightTestFixture fixture, String rootPath,
                                                                  FileFilter fileFilter,
                                                                  Function<PsiFile, ? extends Generator<? extends MadTestingAction>> actions) {
    Generator<File> randomFiles = randomFiles(rootPath, fileFilter);
    return () -> env -> new RunAll()
      .append(() -> {
        File ioFile = env.generateValue(randomFiles, "Working with %s");
        VirtualFile vFile = copyFileToProject(ioFile, fixture, rootPath);
        PsiFile psiFile = fixture.getPsiManager().findFile(vFile);
        if (psiFile instanceof PsiBinaryFile || psiFile instanceof PsiPlainTextFile) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println("Can't check " + vFile + " due to incorrect file type: " + psiFile + " of " + psiFile.getClass());
          return;
        }
        env.executeCommands(Generator.from(data -> data.generate(actions.apply(fixture.getPsiManager().findFile(vFile)))));
      })
      .append(() -> WriteAction.run(() -> {
        for (VirtualFile file : Objects.requireNonNull(fixture.getTempDirFixture().getFile("")).getChildren()) {
          file.delete(fixture);
        }
      }))
      .append(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments())
      .append(() -> UIUtil.dispatchAllInvocationEvents())
      .run();
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
    return Generator.sampledFrom(new DeleteRange(file),
                                 new CheckPsiTextConsistency(file),
                                 new InsertString(file));
  }

  public static boolean isAfterError(PsiFile file, int offset) {
    return SyntaxTraverser.psiTraverser(file).filter(PsiErrorElement.class).find(e -> e.getTextRange().getStartOffset() <= offset) != null;
  }

  public static boolean containsErrorElements(FileViewProvider viewProvider) {
    return ContainerUtil.exists(viewProvider.getAllFiles(), file -> SyntaxTraverser.psiTraverser(file).filter(PsiErrorElement.class).isNotEmpty());
  }

  @NotNull
  public static String getPositionDescription(int offset, Document document) {
    int line = document.getLineNumber(offset);
    int start = document.getLineStartOffset(line);
    int end = document.getLineEndOffset(line);
    int column = offset - start;
    String prefix = document.getText(new TextRange(start, offset)).trim();
    if (prefix.length() > 30) {
      prefix = "..." + prefix.substring(prefix.length() - 30);
    }
    String suffix = StringUtil.shortenTextWithEllipsis(document.getText(new TextRange(offset, end)), 30, 0);
    String text = prefix + "|" + suffix;
    return offset + "(" + line + ":" + column + ") [" + text + "]";
  }

  @NotNull
  static String getIntentionDescription(IntentionAction action) {
    return getIntentionDescription(action.getText(), action);
  }

  @NotNull
  static String getIntentionDescription(String intentionName, IntentionAction action) {
    IntentionAction actual = action;
    while(actual instanceof IntentionActionDelegate) {
      actual = ((IntentionActionDelegate)actual).getDelegate();
    }
    String family = actual.getFamilyName();
    Class<?> aClass = actual.getClass();
    if (actual instanceof QuickFixWrapper) {
      LocalQuickFix fix = ((QuickFixWrapper)actual).getFix();
      family = fix.getFamilyName();
      aClass = fix.getClass();
    }
    return "'" + intentionName + "' (family: '" + family + "'; class: '" + aClass.getName() + "')";
  }

  public static void testFileGenerator(File root, FileFilter filter, int iterationCount, PrintStream out) {
    /* Typical output:
     Roulette wheel generator:
     #10000: sum =  7073 [1: 5597 | 2: 853  | 3: 296  | 4..5: 224  | 6..10: 88   | 11..20: 15   | 21..30: 0    | 31..50: 0    | 51..100: 0    | 101..200: 0    | 201+: 0]
     Plain generator:
     #10000: sum =  2477 [1: 1450 | 2: 393  | 3: 171  | 4..5: 143  | 6..10: 151  | 11..20: 102  | 21..30: 27   | 31..50: 10   | 51..100: 22   | 101..200: 6    | 201+: 2]
     */
    for (boolean roulette : new boolean[]{true, false}) {
      out.println("Testing " + (roulette ? "roulette" : "plain") + " generator");
      TObjectLongHashMap<String> fileMap = new TObjectLongHashMap<>();
      Generator<File> generator = randomFiles(root.getPath(), filter, roulette);
      MadTestingAction action = env -> {
        long lastTime = System.nanoTime(), startTime = lastTime;
        for (int iteration = 1; iteration <= iterationCount; iteration++) {
          File file = env.generateValue(generator, null);
          if (!fileMap.containsKey(file.getPath())) {
            fileMap.put(file.getPath(), 1);
          }
          else {
            fileMap.increment(file.getPath());
          }
          long curTime = System.nanoTime();
          if (iteration <= 10) {
            out.print("#" + iteration + " = " + (curTime - lastTime) / 1_000_000 + "ms");
            if (iteration == 10) {
              out.println();
            } else {
              out.print("; ");
            }
            lastTime = curTime;
          }
          if (iteration == iterationCount || curTime - lastTime > TimeUnit.SECONDS.toNanos(5)) {
            lastTime = curTime;
            long[] stops = {1, 2, 3, 5, 10, 20, 30, 50, 100, 200, Long.MAX_VALUE};
            int[] histogram = new int[stops.length];
            fileMap.forEachEntry((path, count) -> {
              int pos = Arrays.binarySearch(stops, count);
              if (pos < 0) {
                pos = -pos - 1;
              }
              histogram[pos]++;
              return true;
            });
            StringBuilder report = new StringBuilder();
            for (int i = 0; i < stops.length; i++) {
              String range = i == 0 || stops[i - 1] == stops[i] - 1 ? String.valueOf(stops[i]) :
                             (stops[i - 1] + 1) + (stops[i] == Long.MAX_VALUE ? "+" : ".."+stops[i]);
              report.append(String.format(Locale.ENGLISH, "%s: %-5d| ", range, histogram[i]));
            }
            out.printf("#%-5d: sum = %5d [%s]%n", iteration, Arrays.stream(histogram).sum(),
                       report.toString().replaceFirst("[\\s|]+$", ""));
          }
        }
        out.println("Total time: " + (System.nanoTime() - startTime) / 1_000_000 + "ms");
      };
      PropertyChecker.customized().withIterationCount(1).checkScenarios(() -> action);
    }
  }

  private static class FileGenerator implements Function<DataStructure, File> {
    private static final com.intellij.util.Function<File, JBIterable<File>> FS_TRAVERSAL =
      TreeTraversal.PRE_ORDER_DFS.traversal((File f) -> f.isDirectory() ? Arrays.asList(Objects.requireNonNull(f.listFiles())) : Collections.emptyList());
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
        File[] children = file.listFiles(f -> !exhausted.contains(f) && containsAtLeastOneFileDeep(f) && myFilter.accept(f));
        if (children == null) {
          return file;
        }
        if (children.length == 0) {
          exhausted.add(file);
          return null;
        }

        List<File> toChoose = preferDirs(data, children);
        Collections.sort(toChoose, Comparator.comparing(File::getName));
        File chosen = data.generate(Generator.sampledFrom(toChoose));
        File generated = generateRandomFile(data, chosen, exhausted);
        if (generated != null) {
          return generated;
        }
      }
    }

    private static boolean containsAtLeastOneFileDeep(File root) {
      return FS_TRAVERSAL.fun(root).find(f -> f.isFile()) != null;
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
      return data.generate(Generator.integers(0, ratio - 1)) != 0 ? dirs : files;
    }
  }

  private static class RouletteWheelFileGenerator implements Function<DataStructure, File> {
    private final File myRoot;
    private final FileFilter myFilter;
    private static final File[] EMPTY_DIRECTORY = new File[0];
    private final SoftFactoryMap<File, File[]> myChildrenCache = new SoftFactoryMap<File, File[]>() {
      @Override
      protected File[] create(File f) {
        File[] files = f.listFiles(child -> myFilter.accept(child) && child.isFile() || FileGenerator.containsAtLeastOneFileDeep(child));
        return files != null && files.length == 0 ? EMPTY_DIRECTORY : files;
      }
    };

    public RouletteWheelFileGenerator(File root, FileFilter filter) {
      myRoot = root;
      myFilter = filter;
    }

    @Override
    public File apply(DataStructure data) {
      return generateRandomFile(data, myRoot);
    }

    @Nullable
    private File generateRandomFile(DataStructure data, File file) {
      while (true) {
        File[] children = myChildrenCache.get(file);
        if (children == null) {
          return file;
        }
        if (children == EMPTY_DIRECTORY) {
          return null;
        }
        Arrays.sort(children, Comparator.comparing(File::getName));
        int[] weights = new int[children.length];
        int totalWeight = 0;
        for (int i = 0; i < children.length; i++) {
          File[] grandChildren = myChildrenCache.get(children[i]);
          int weight;
          if (grandChildren == null) {
            weight = 1;
          }
          else {
            weight = Stream.of(grandChildren).mapToInt(f -> f.isDirectory() ? 5 : 1).sum();
          }
          weights[i] = weight;
          totalWeight += weight;
        }
        int value = data.generate(Generator.integers(0, totalWeight));
        for (int i = 0; i < children.length; i++) {
          value -= weights[i];
          if (value < 0) {
            File chosen = children[i];
            File generated = generateRandomFile(data, chosen);
            if (generated != null) {
              return generated;
            }
            break;
          }
        }
      }
    }
  }
}
