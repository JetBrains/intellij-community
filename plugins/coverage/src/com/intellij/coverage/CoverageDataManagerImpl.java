package com.intellij.coverage;

import com.intellij.codeInsight.CodeInsightBundle;
import static com.intellij.coverage.PackageAnnotator.ClassCoverageInfo;
import static com.intellij.coverage.PackageAnnotator.PackageCoverageInfo;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author ven
 */
public class CoverageDataManagerImpl extends CoverageDataManager {
  private List<CoverageSuiteListener> myListeners = new ArrayList<CoverageSuiteListener>();
  private static final Logger LOG = Logger.getInstance("#" + CoverageDataManagerImpl.class.getName());
  @NonNls
  private static final String SUITE = "SUITE";

  private final Map<String, PackageCoverageInfo> myPackageCoverageInfos = new HashMap<String, PackageCoverageInfo>();
  private final Map<Pair<String, Module>, PackageCoverageInfo> myDirCoverageInfos = new HashMap<Pair<String, Module>, PackageCoverageInfo>();
  private final Map<Pair<String, Module>, PackageCoverageInfo> myTestDirCoverageInfos = new HashMap<Pair<String, Module>, PackageCoverageInfo>();
  private final Map<String, ClassCoverageInfo> myClassCoverageInfos = new HashMap<String, ClassCoverageInfo>();
  private static final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, ApplicationManager.getApplication());
  private final Project myProject;
  private final Set<CoverageSuiteImpl> myCoverageSuites = new HashSet<CoverageSuiteImpl>();
  private boolean myIsProjectClosing = false;
  private ProjectManagerAdapter myProjectManagerListener;

  private final Object myLock = new Object();
  private boolean mySubCoverageIsActive;

  public CoverageSuite getCurrentSuite() {
    return myCurrentSuite;
  }

  private CoverageSuiteImpl myCurrentSuite;
  private EditorFactoryListener myEditorFactoryListener;

  public CoverageDataManagerImpl(final Project project) {
    myProject = project;
  }


  @NotNull @NonNls
  public String getComponentName() {
    return "CoverageDataManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    //noinspection unchecked
    for (Element suiteElement : (Iterable<Element>) element.getChildren(SUITE)) {
      CoverageSuiteImpl suite = new CoverageSuiteImpl();
      try {
        suite.readExternal(suiteElement);
        myCoverageSuites.add(suite);
      }
      catch (NumberFormatException e) {
        //try next suite
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    for (CoverageSuiteImpl coverageSuite : myCoverageSuites) {
      final Element suiteElement = new Element(SUITE);
      element.addContent(suiteElement);
      coverageSuite.writeExternal(suiteElement);
    }
  }

  public CoverageSuite addCoverageSuite(final String name, final CoverageFileProvider fileProvider, final String[] filters, final long lastCoverageTimeStamp,
                                        @Nullable final String suiteToMergeWith,
                                        final CoverageRunner coverageRunner,
                                        final boolean collectLineInfo,
                                        final boolean tracingEnabled) {
    CoverageSuiteImpl suite = new CoverageSuiteImpl(name, fileProvider, filters, lastCoverageTimeStamp, suiteToMergeWith, collectLineInfo, tracingEnabled, false, coverageRunner);
    if (suiteToMergeWith == null || !name.equals(suiteToMergeWith)) {
      removeCoverageSuite(suite);
    }
    myCoverageSuites.remove(suite); // remove previous instance
    myCoverageSuites.add(suite); // add new instance
    return suite;
  }

  @Override
  public CoverageSuite addCoverageSuite(final CoverageEnabledConfiguration config) {
    final String name = config.getName() + " Coverage Results";
    CoverageSuiteImpl suite =
      new CoverageSuiteImpl(name, new DefaultCoverageFileProvider(new File(config.getCoverageFilePath())),
                            config.getPatterns(), new Date().getTime(), config.getSuiteToMergeWith(), config.isTrackPerTestCoverage() && !config.isSampling(),
                            !config.isSampling(), config.isTrackTestFolders(), config.getCoverageRunner());
    if (config.getSuiteToMergeWith() == null || !name.equals(config.getSuiteToMergeWith())) {
      removeCoverageSuite(suite);
    }
    myCoverageSuites.remove(suite); // remove previous instance
    myCoverageSuites.add(suite); // add new instance
    return suite;
  }

  public void removeCoverageSuite(CoverageSuite suite) {
    final String fileName = suite.getCoverageDataFileName();
    FileUtil.delete(new File(fileName));
    FileUtil.delete(getTracesDirectory(fileName));
    myCoverageSuites.remove(suite);
    if (myCurrentSuite == suite) {
      chooseSuite(null);
    }
  }

  public CoverageSuite[] getSuites() {
    return myCoverageSuites.toArray(new CoverageSuite[myCoverageSuites.size()]);
  }

  public String getDirCoverageInformationString(PsiDirectory directory) {
    if (myCurrentSuite == null) return null;
    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (psiPackage == null) return null;
    final String packageFQName = psiPackage.getQualifiedName();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final Module module = projectFileIndex.getModuleForFile(directory.getVirtualFile());
    if (module == null) return null;
    final boolean isInTestContent = projectFileIndex.isInTestSourceContent(directory.getVirtualFile());
    if (!myCurrentSuite.isTrackTestFolders() && isInTestContent) return null;
    final Pair<String, Module> qualifiedPair = new Pair<String, Module>(packageFQName, module);
    return isInTestContent ? getCoverageInformationString(myTestDirCoverageInfos.get(qualifiedPair))
                           : getCoverageInformationString(myDirCoverageInfos.get(qualifiedPair));
  }

  public String getPackageCoverageInformationString(final String packageFQName,
                                                    @Nullable final Module module) {
    PackageCoverageInfo info;
    if (module != null) {
      final Pair<String, Module> p = new Pair<String, Module>(packageFQName, module);
      info = myDirCoverageInfos.get(p);
      final PackageCoverageInfo testInfo = myTestDirCoverageInfos.get(p);
      if (testInfo != null) {
        if (info == null) {
          return getCoverageInformationString(testInfo);
        } else {
          final PackageCoverageInfo coverageInfo = new PackageCoverageInfo();
          coverageInfo.totalClassCount = info.totalClassCount + testInfo.totalClassCount;
          coverageInfo.coveredClassCount = info.coveredClassCount + testInfo.coveredClassCount;

          coverageInfo.totalLineCount = info.totalLineCount + testInfo.totalLineCount;
          coverageInfo.coveredLineCount = info.coveredLineCount + testInfo.coveredLineCount;
          return getCoverageInformationString(coverageInfo);
        }
      }
    }
    else {
      info = myPackageCoverageInfos.get(packageFQName);
    }
    return getCoverageInformationString(info);
  }

  private String getCoverageInformationString(PackageCoverageInfo info) {
    if (info == null) return null;
    if (info.totalClassCount == 0 || info.totalLineCount == 0) return null;
    if (mySubCoverageIsActive) {
      return info.coveredClassCount + info.coveredLineCount > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredClassCount / info.totalClassCount * 100) +  "% classes, " +
           (int)((double)info.coveredLineCount / info.totalLineCount * 100) + "% lines covered";
  }

  public String getClassCoverageInformationString(String classFQName) {
    final ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    if (info == null) return null;
    if (info.totalMethodCount == 0 || info.totalLineCount == 0) return null;
    if (mySubCoverageIsActive){
      return info.coveredMethodCount + info.fullyCoveredLineCount + info.partiallyCoveredLineCount > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredMethodCount / info.totalMethodCount * 100) +  "% methods, " +
           (int)((double)(info.fullyCoveredLineCount + info.partiallyCoveredLineCount) / info.totalLineCount * 100) + "% lines covered";
  }

  public void chooseSuite(final CoverageSuite suite) {
    myCurrentSuite = (CoverageSuiteImpl)suite;
    myAlarm.cancelAllRequests();
    myPackageCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myClassCoverageInfos.clear();

    if (suite == null) {
      triggerPresentationUpdate();
      return;
    }

    final boolean suiteFileExists = myCurrentSuite.getCoverageDataFileProvider().ensureFileExists();
    if (!suiteFileExists) {
      chooseSuite(null);
      return;
    }

    renewCoverageData(suite);

    fireAfterSuiteChosen();
  }

  public void renewCoverageData(final CoverageSuite suite) {
    final List<PsiPackage> packages = getCurrentSuitePackages();
    final List<PsiClass> classes = getCurrentSuiteClasses();

    if (!packages.isEmpty() || !classes.isEmpty()) {
      myAlarm.addRequest(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          for (PsiPackage aPackage : packages) {
            new PackageAnnotator(aPackage).annotate((CoverageSuiteImpl)suite, new PackageAnnotator.Annotator() {
              public void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo) {
                myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
              }

              public void annotateSourceDirectory(String packageQualifiedName,
                                                  PackageCoverageInfo dirCoverageInfo,
                                                  Module module) {
                final Pair<String, Module> p = new Pair<String, Module>(packageQualifiedName, module);
                myDirCoverageInfos.put(p, dirCoverageInfo);
              }

              public void annotateTestDirectory(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, Module module) {
                final Pair<String, Module> p = new Pair<String, Module>(packageQualifiedName, module);
                myTestDirCoverageInfos.put(p, packageCoverageInfo);
              }

              public void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo) {
                myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
              }
            });
          }

          triggerPresentationUpdate();
        }
      }, 100);
    }
  }

  public void coverageGathered(@NotNull final CoverageSuite suite) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myCurrentSuite != null && !myCurrentSuite.equals(suite)) {
          final String message = CodeInsightBundle.message("display.coverage.prompt", suite.getPresentableName());
          if (Messages.showYesNoDialog(message, CodeInsightBundle.message("code.coverage"), Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
            chooseSuite(suite);
          }
        }
        else {
          chooseSuite(suite);
        }
      }
    });
  }

  private void triggerPresentationUpdate() {
    ApplicationManager.getApplication().invokeLater(new Runnable () {
      public void run() {
        renewInformationInEditors();

        if (!myProject.isDisposed()) {
          ProjectView.getInstance(myProject).refresh();
        }
      }
    });
  }

  private void renewInformationInEditors() {
    final Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    for (Editor editor : allEditors) {
      applyInformationToEditor(editor);
    }
  }

  private @NotNull List<PsiPackage> getCurrentSuitePackages() {
    List<PsiPackage> packages = new ArrayList<PsiPackage>();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final String[] filters = myCurrentSuite.getFilteredPackageNames();
    if (filters.length == 0) {
      if (myCurrentSuite.getFilteredClassNames().length > 0) return Collections.emptyList();

      final PsiPackage defaultPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage("");
      if (defaultPackage != null) {
        packages.add(defaultPackage);
      }
    } else {
      for (String filter : filters) {
        final PsiPackage psiPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(filter);
        if (psiPackage != null) {
          packages.add(psiPackage);
        }
      }
    }

    return packages;
  }

  private @NotNull List<PsiClass> getCurrentSuiteClasses() {
    List<PsiClass> classes = new ArrayList<PsiClass>();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final String[] classNames = myCurrentSuite.getFilteredClassNames();
    if (classNames.length > 0) {
      for (String className : classNames) {
        final PsiClass aClass =
          JavaPsiFacade.getInstance(psiManager.getProject()).findClass(className.replace("$", "."), GlobalSearchScope.allScope(myProject));
        if (aClass != null) {
          classes.add(aClass);
        }
      }
    }

    return classes;
  }

  private boolean isUnderFilteredPackages(final PsiClassOwner javaFile, final List<PsiPackage> packages) {
    final String hisPackageName = javaFile.getPackageName();
    PsiPackage hisPackage = JavaPsiFacade.getInstance(myProject).findPackage(hisPackageName);
    if (hisPackage == null) return false;
    for (PsiPackage aPackage : packages) {
      if (PsiTreeUtil.isAncestor(aPackage, hisPackage, false)) return true;
    }
    return false;
  }

  private void applyInformationToEditor(final Editor editor) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    final Document document = editor.getDocument();
    final PsiFile psiFile = documentManager.getPsiFile(document);
    if (psiFile instanceof PsiClassOwner && psiFile.isPhysical()) {
      final SrcFileAnnotator annotator = new SrcFileAnnotator(psiFile, editor);
      annotator.hideCoverageData();
      if (myCurrentSuite != null) {
        final List<PsiPackage> packages = getCurrentSuitePackages();
        if (isUnderFilteredPackages((PsiClassOwner)psiFile, packages)) {
          Disposer.register(myProject, annotator);
          annotator.showCoverageInformation(myCurrentSuite);
        } else {
          final List<PsiClass> classes = getCurrentSuiteClasses();
          for (PsiClass aClass : classes) {
            final PsiFile containingFile = aClass.getContainingFile();
            if (psiFile.equals(containingFile)) {
              Disposer.register(myProject, annotator);
              annotator.showCoverageInformation(myCurrentSuite);
              break;
            }
          }
        }
      }
    }
  }

  public void projectOpened() {
    myEditorFactoryListener = new EditorFactoryListener() {
      public void editorCreated(EditorFactoryEvent event) {
        final Editor editor = event.getEditor();
        if (editor.getProject() != null && editor.getProject() != myProject) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) return;
            applyInformationToEditor(editor);
          }
        });
      }

      public void editorReleased(EditorFactoryEvent event) {}
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
    myProjectManagerListener = new ProjectManagerAdapter() {
      public void projectClosing(Project project) {
        synchronized (myLock) {
          if (project.equals(myProject)) {
            myIsProjectClosing = true;
          }
        }
      }
    };
    ProjectManager.getInstance().addProjectManagerListener(myProjectManagerListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
        ProjectManager.getInstance().removeProjectManagerListener(myProjectManagerListener);
      }
    });
  }

  public void projectClosed() {
  }

  public <T> T doInReadActionIfProjectOpen(Computable<T> computation) {
    synchronized(myLock) {
      if (myIsProjectClosing) return null;
      return ApplicationManager.getApplication().runReadAction(computation);
    }
  }

  public void selectSubCoverage(@NotNull final CoverageSuite suite, final List<String> testNames) {
    ((CoverageSuiteImpl)suite).restoreCoverageData();
    final ProjectData data = ((CoverageSuiteImpl)suite).getCoverageData(this);
    if (data == null) return;
    mySubCoverageIsActive = true;
    final Map<String, Set<Integer>> executionTrace = new HashMap<String, Set<Integer>>();
    final String fileName = suite.getCoverageDataFileName();
    final File tracesDir = getTracesDirectory(fileName);
    for (String testName : testNames) {
      final File file = new File(tracesDir, testName + ".tr");
      if (file.exists()) {
        DataInputStream in = null;
        try {
          in = new DataInputStream(new FileInputStream(file));
          int traceSize = in.readInt();
          for (int i = 0; i < traceSize; i++) {
            final String className = in.readUTF();
            final int linesSize = in.readInt();
            Set<Integer> lines = executionTrace.get(className);
            if (lines == null) {
              lines = new HashSet<Integer>();
              executionTrace.put(className, lines);
            }
            for(int l = 0; l < linesSize; l++) {
              lines.add(in.readInt());
            }
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
        finally {
          try {
            in.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    }
    final ProjectData projectData = new ProjectData();
    for (String className : executionTrace.keySet()) {
      ClassData loadedClassData = projectData.getClassData(className);
      if (loadedClassData == null) {
        loadedClassData = projectData.getOrCreateClassData(className);
      }
      for (Integer line : executionTrace.get(className)) {
        loadedClassData.getOrCreateLine(line.intValue(), null).setStatus(LineCoverage.FULL);
      }
    }
    ((CoverageSuiteImpl)suite).setCoverageData(projectData);
    renewCoverageData(suite);
  }

  private File getTracesDirectory(final String fileName) {
    return new File(new File(fileName).getParentFile(), FileUtil.getNameWithoutExtension(new File(fileName)));
  }

  public void restoreMergedCoverage(@NotNull final CoverageSuite suite) {
    mySubCoverageIsActive = false;
    ((CoverageSuiteImpl)suite).restoreCoverageData();
    renewCoverageData(suite);
  }

  @Override
  public void addSuiteListener(final CoverageSuiteListener listener, Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  public void fireBeforeSuiteChosen() {
    for (CoverageSuiteListener listener : myListeners) {
      listener.beforeSuiteChosen();
    }
  }

  public void fireAfterSuiteChosen() {
    for (CoverageSuiteListener listener : myListeners) {
      listener.afterSuiteChosen();
    }
  }

  public boolean isSubCoverageActive() {
    return mySubCoverageIsActive;
  }
}
