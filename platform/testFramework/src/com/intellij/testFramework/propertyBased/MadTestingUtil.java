// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.GenerationEnvironment;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author peter
 */
public final class MadTestingUtil {
  private static final Logger LOG = Logger.getInstance(MadTestingUtil.class);
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

  private static <E extends Throwable> void watchDocumentChanges(ThrowableRunnable<E> r, Consumer<? super DocumentEvent> eventHandler) throws E {
    Disposable disposable = Disposer.newDisposable();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
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
          () -> EditorHistoryManager.getInstance(project).removeAllFiles(),
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
    watchDocumentChanges(() -> label.revert(project, PlatformTestUtil.getOrCreateProjectBaseDir(project)),
                               __ -> {
                                 PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                                 if (documentManager.getUncommittedDocuments().length > 3) {
                                   documentManager.commitAllDocuments();
                                 }
                               });
  }

  /**
   * Enables all inspections in the test project profile except for "HighlightVisitorInternal" and other passed inspections.<p>
   *
   * "HighlightVisitorInternal" inspection has error-level by default and highlights the first token from erroneous range,
   * which is not very stable and also masks other warning-level inspections available on the same token.
   *
   * @param except short names of inspections to disable
   */
  public static void enableAllInspections(@NotNull Project project, String... except) {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    InspectionProfileImpl profile = new InspectionProfileImpl("allEnabled");
    profile.enableAllTools(project);

    disableInspection(project, profile, "HighlightVisitorInternal");

    for (String shortId : except) {
      disableInspection(project, profile, shortId);
    }
    replaceProfile(project, profile);
  }

  public static void enableDefaultInspections(@NotNull Project project) {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    InspectionProfileImpl profile = new InspectionProfileImpl("defaultInspections");
    replaceProfile(project, profile);
  }

  private static void replaceProfile(@NotNull Project project, InspectionProfileImpl profile) {
    // instantiate all tools to avoid extension loading in inconvenient moment
    profile.getAllEnabledInspectionTools(project).forEach(state -> state.getTool().getTool());

    ProjectInspectionProfileManager manager = (ProjectInspectionProfileManager)InspectionProjectProfileManager.getInstance(project);
    manager.addProfile(profile);
    InspectionProfileImpl prev = manager.getCurrentProfile();
    manager.setCurrentProfile(profile);
    Disposer.register(((ProjectEx)project).getEarlyDisposable(), () -> {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      manager.setCurrentProfile(prev);
      manager.deleteProfile(profile);
    });
  }

  private static void disableInspection(Project project, InspectionProfileImpl profile, String shortId) {
    ToolsImpl tools = profile.getToolsOrNull(shortId, project);
    if (tools != null) {
      tools.setEnabled(false);
    }
  }

  public static Generator<File> randomFiles(String rootPath, FileFilter fileFilter) {
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
    Function<GenerationEnvironment, File> generator =
      useRouletteWheel ? new RouletteWheelFileGenerator(root, interestingIdeaFiles) : new FileGenerator(root, interestingIdeaFiles);
    return Generator.from(generator)
      .suchThat(new Predicate<>() {
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
   * Finds files under {@code rootPath} (e.g. test data root) satisfying {@code fileFilter condition} (e.g. correct extension) and uses {@code actions} to generate actions on those files (e.g. invoke completion/intentions or random editing).
   * Almost: the files with same paths and contents are created inside the test project, then the actions are executed on them.
   * Note that the test project contains only one file at each moment, so it's best to test actions that don't require much environment.
   */
  @NotNull
  public static Supplier<MadTestingAction> actionsOnFileContents(CodeInsightTestFixture fixture, String rootPath,
                                                                 FileFilter fileFilter,
                                                                 Function<? super PsiFile, ? extends Generator<? extends MadTestingAction>> actions) {
    return performOnFileContents(fixture, rootPath, fileFilter, (env, vFile) ->
      env.executeCommands(Generator.from(data -> data.generate(actions.apply(fixture.getPsiManager().findFile(vFile))))));
  }

  /**
   * Finds files under {@code rootPath} (e.g. test data root) satisfying {@code fileFilter condition} (e.g. correct extension) and invokes {@code action} on those files.
   * Almost: the files with same paths and contents are created inside the test project, then the actions are executed on them.
   * Note that the test project contains only one file at each moment, so it's best to test actions that don't require much environment.
   */
  @NotNull
  public static Supplier<MadTestingAction> performOnFileContents(CodeInsightTestFixture fixture,
                                                                 String rootPath,
                                                                 FileFilter fileFilter,
                                                                 BiConsumer<? super ImperativeCommand.Environment, ? super VirtualFile> action) {
    Generator<File> randomFiles = randomFiles(rootPath, fileFilter);
    return () -> env -> new RunAll(
      () -> {
        File ioFile = env.generateValue(randomFiles, "Working with %s");
        VirtualFile vFile = copyFileToProject(ioFile, fixture, rootPath);
        PsiFile psiFile = fixture.getPsiManager().findFile(vFile);
        if (psiFile instanceof PsiBinaryFile || psiFile instanceof PsiPlainTextFile) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println("Can't check " + vFile + " due to incorrect file type: " + psiFile + " of " + psiFile.getClass());
          return;
        }
        action.accept(env, vFile);
      },
      () -> WriteAction.run(() -> {
        for (VirtualFile file : Objects.requireNonNull(fixture.getTempDirFixture().getFile("")).getChildren()) {
          file.delete(fixture);
        }
      }),
      () -> PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments(),
      () -> UIUtil.dispatchAllInvocationEvents()
    ).run();
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

      Matcher rootPackageMatcher = Pattern.compile("/com/|/org/|/onair/").matcher(path);
      if (rootPackageMatcher.find()) {
        path = path.substring(rootPackageMatcher.start() + 1);
      }

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
  public static Generator<MadTestingAction> randomEditsWithReparseChecks(@NotNull PsiFile file) {
    return Generator.sampledFrom(
      new InsertString(file),
      new DeleteRange(file),
      new CommitDocumentAction(file),
      new CheckPsiTextConsistency(file)
    );
  }

  /**
   * @param skipCondition should return {@code true} if particular accessor method should be ignored
   * @return function returning generator of actions checking that
   * read accessors on all PSI elements in the file don't throw exceptions when invoked.
   */
  @NotNull
  public static Function<PsiFile, Generator<? extends MadTestingAction>> randomEditsWithPsiAccessorChecks(@NotNull Condition<? super Method> skipCondition) {
    return file -> Generator.sampledFrom(
      new InsertString(file),
      new DeleteRange(file),
      new CommitDocumentAction(file),
      new CheckPsiReadAccessors(file, skipCondition),
      new ResolveAllReferences(file)
    );
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
    return offset + "(" + (line + 1) + ":" + (column + 1) + ") [" + text + "]";
  }

  @NotNull
  static String getIntentionDescription(IntentionAction action) {
    return getIntentionDescription(action.getText(), action);
  }

  @NotNull
  static String getIntentionDescription(String intentionName, IntentionAction action) {
    IntentionAction actual = IntentionActionDelegate.unwrap(action);
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
     #10000: sum =  5281 [1: 3770 | 2: 767  | 3: 272  | 4..5: 232  | 6..10: 146  | 11..20: 67   | 21..30: 12   | 31..50: 12   | 51..100: 3    | 101..200: 0    | 201+: 0]
     Plain generator:
     #10000: sum =  2504 [1: 1478 | 2: 391  | 3: 159  | 4..5: 155  | 6..10: 156  | 11..20: 103  | 21..30: 22   | 31..50: 8    | 51..100: 22   | 101..200: 9    | 201+: 1]
     */
    for (boolean roulette : new boolean[]{true, false}) {
      out.println("Testing " + (roulette ? "roulette" : "plain") + " generator");
      ObjectIntHashMap<String> fileMap = new ObjectIntHashMap<>();
      Generator<File> generator = randomFiles(root.getPath(), filter, roulette);
      MadTestingAction action = env -> {
        long lastTime = System.nanoTime(), startTime = lastTime;
        for (int iteration = 1; iteration <= iterationCount; iteration++) {
          File file = env.generateValue(generator, null);
          assert filter.accept(file);
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
            out.println(getHistogramReport(fileMap, iteration));
          }
        }
        out.println("Total time: " + (System.nanoTime() - startTime) / 1_000_000 + "ms");
      };
      PropertyChecker.customized().withIterationCount(1).checkScenarios(() -> action);
    }
  }

  @NotNull
  private static String getHistogramReport(ObjectIntHashMap<String> fileMap, int iteration) {
    long[] stops = {1, 2, 3, 5, 10, 20, 30, 50, 100, 200, Long.MAX_VALUE};
    int[] histogram = new int[stops.length];
    for (ObjectIntMap.Entry<String> entry : fileMap.entries()) {
      int count = entry.getValue();
      int pos = Arrays.binarySearch(stops, count);
      if (pos < 0) {
        pos = -pos - 1;
      }
      histogram[pos]++;
    }
    StringBuilder report = new StringBuilder();
    for (int i = 0; i < stops.length; i++) {
      String range = i == 0 || stops[i - 1] == stops[i] - 1 ? String.valueOf(stops[i]) :
                     (stops[i - 1] + 1) + (stops[i] == Long.MAX_VALUE ? "+" : ".."+stops[i]);
      report.append(String.format(Locale.ENGLISH, "%s: %-5d| ", range, histogram[i]));
    }
    return String.format(Locale.ENGLISH, "#%-5d: sum = %5d [%s]", iteration,
                         Arrays.stream(histogram).sum(), report.toString().replaceFirst("[\\s|]+$", ""));
  }

  private static final class FileGenerator implements Function<GenerationEnvironment, File> {
    private static final com.intellij.util.Function<File, JBIterable<File>> FS_TRAVERSAL =
      TreeTraversal.PRE_ORDER_DFS.traversal((File f) -> f.isDirectory() ? Arrays.asList(Objects.requireNonNull(f.listFiles())) : Collections.emptyList());
    private final File myRoot;
    private final FileFilter myFilter;

    private FileGenerator(File root, FileFilter filter) {
      myRoot = root;
      myFilter = filter;
    }

    @Override
    public File apply(GenerationEnvironment data) {
      return generateRandomFile(data, myRoot, new HashSet<>());
    }

    @Nullable
    private File generateRandomFile(GenerationEnvironment data, File file, Set<? super File> exhausted) {
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
        toChoose.sort(Comparator.comparing(File::getName));
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

    private static List<File> preferDirs(GenerationEnvironment data, File[] children) {
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

  private static final class RouletteWheelFileGenerator implements Function<GenerationEnvironment, File> {
    private final File myRoot;
    private final FileFilter myFilter;
    private static final File[] EMPTY_DIRECTORY = new File[0];
    private final SoftFactoryMap<File, File[]> myChildrenCache = new SoftFactoryMap<>() {
      @Override
      protected File[] create(File f) {
        File[] files = f.listFiles(child -> myFilter.accept(child) && (child.isFile() || FileGenerator.containsAtLeastOneFileDeep(child)));
        return files != null && files.length == 0 ? EMPTY_DIRECTORY : files;
      }
    };

    private RouletteWheelFileGenerator(File root, FileFilter filter) {
      myRoot = root;
      myFilter = filter;
    }

    @Override
    public File apply(GenerationEnvironment data) {
      return generateRandomFile(data, myRoot, new HashSet<>());
    }

    @Nullable
    private File generateRandomFile(GenerationEnvironment data, File file, Set<File> exhausted) {
      File[] children = myChildrenCache.get(file);
      if (children == null) {
        return file;
      }
      if (children == EMPTY_DIRECTORY) {
        return null;
      }
      Arrays.sort(children, Comparator.comparing(File::getName));
      while (true) {
        int[] weights = Arrays.stream(children).mapToInt(child -> estimateWeight(child, exhausted)).toArray();
        int index;
        try {
          index = spin(data, weights);
        }
        catch (RuntimeException e) {
          if ("org.jetbrains.jetCheck.CannotRestoreValue".equals(e.getClass().getName())) {
            throw new RuntimeException("Directory structure changed in " + file + " or its direct children?", e);
          }
          throw e;
        }
        if (index == -1) return null;
        File chosen = children[index];
        File generated = generateRandomFile(data, chosen, exhausted);
        if (generated != null) {
          return generated;
        }
        exhausted.add(chosen);
      }
    }

    private static int spin(@NotNull GenerationEnvironment data, int @NotNull [] weights) {
      int totalWeight = Arrays.stream(weights).sum();
      if (totalWeight == 0) return -1;
      int value = data.generate(Generator.integers(0, totalWeight));
      for (int i = 0; i < weights.length; i++) {
        value -= weights[i];
        if (value < 0) {
          return i;
        }
      }
      return -1;
    }

    private int estimateWeight(File file, @NotNull Set<File> exhausted) {
      if (exhausted.contains(file)) return 0;
      File[] children = myChildrenCache.get(file);
      if (children == null) return 1;
      return Stream.of(children).mapToInt(f -> exhausted.contains(f) ? 0 : f.isDirectory() ? 5 : 1).sum();
    }
  }
}
