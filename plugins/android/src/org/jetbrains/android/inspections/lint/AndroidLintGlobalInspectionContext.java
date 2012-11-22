package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.LintCliXmlParser;
import com.android.tools.lint.LombokParser;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.*;
import com.android.tools.lint.detector.api.*;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidLintGlobalInspectionContext implements GlobalInspectionContextExtension<AndroidLintGlobalInspectionContext> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.AndroidLintGlobalInspectionContext");

  static final Key<AndroidLintGlobalInspectionContext> ID = Key.create("AndroidLintGlobalInspectionContext");
  private Map<Issue, Map<File, List<ProblemData>>> myResults;

  @Override
  public Key<AndroidLintGlobalInspectionContext> getID() {
    return ID;
  }

  @Override
  public void performPreRunActivities(List<Tools> globalTools, List<Tools> localTools, final GlobalInspectionContext context) {
    final Project project = context.getProject();

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return;
    }

    final List<Issue> issues = AndroidLintExternalAnnotator.getIssuesFromInspections(project, null);
    if (issues.size() == 0) {
      return;
    }

    final Map<Issue, Map<File, List<ProblemData>>> problemMap = new HashMap<Issue, Map<File, List<ProblemData>>>();
    final Set<VirtualFile> allContentRoots = new HashSet<VirtualFile>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (AndroidFacet.getInstance(module) != null) {
        final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        Collections.addAll(allContentRoots, contentRoots);
      }
    }

    final File[] ioContentRoots = toIoFiles(allContentRoots);
    final AnalysisScope scope = context.getRefManager().getScope();

    final LintClient client = new MyLintClient(project, problemMap, scope, issues);
    final LintDriver lint = new LintDriver(new BuiltinIssueRegistry(), client);

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      ProgressWrapper.unwrap(indicator).setText("Running Android Lint");
    }

    lint.analyze(Arrays.asList(ioContentRoots), EnumSet
      .of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.PROGUARD_FILE, Scope.ALL_JAVA_FILES, Scope.ALL_RESOURCE_FILES, Scope.RESOURCE_FILE));

    myResults = problemMap;
  }

  private static File[] toIoFiles(@NotNull Collection<VirtualFile> files) {
    final File[] result = new File[files.size()];

    int i = 0;
    for (VirtualFile file : files) {
      result[i++] = new File(file.getPath());
    }
    return result;
  }

  @Nullable
  public Map<Issue, Map<File, List<ProblemData>>> getResults() {
    return myResults;
  }

  @Override
  public void performPostRunActivities(List<InspectionProfileEntry> inspections, final GlobalInspectionContext context) {
  }

  @Override
  public void cleanup() {
  }

  private static class MyLintClient extends LintClient {
    private final Project myProject;
    private final Map<Issue, Map<File, List<ProblemData>>> myProblemMap;
    private final AnalysisScope myScope;
    private final Collection<Issue> myIssues;

    private MyLintClient(@NotNull Project project,
                         @NotNull Map<Issue, Map<File, List<ProblemData>>> problemMap,
                         @NotNull AnalysisScope scope, 
                         @NotNull Collection<Issue> issues) {
      myProject = project;
      myProblemMap = problemMap;
      myScope = scope;
      myIssues = issues;
    }

    @Override
    public Configuration getConfiguration(com.android.tools.lint.detector.api.Project project) {
      return new IntellijLintConfiguration(myIssues);
    }

    @Override
    public void report(Context context, Issue issue, Severity severity, Location location, String message, Object data) {
      VirtualFile vFile = null;
      File file = null;

      if (location != null) {
        file = location.getFile();

        if (file != null) {
          vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        }
      }
      else if (context.getProject() != null) {
        final Module module = findModuleForLintProject(myProject, context.getProject());

        if (module != null) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          vFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;
          
          if (vFile != null) {
            file = new File(vFile.getPath());
          }
        }
      }

      if (vFile != null && myScope.contains(vFile)) {
        try {
          file = file.getCanonicalFile();
        }
        catch (IOException e) {
          LOG.error(e);
          return;
        }

        Map<File, List<ProblemData>> file2ProblemList = myProblemMap.get(issue);
        if (file2ProblemList == null) {
          file2ProblemList = new HashMap<File, List<ProblemData>>();
          myProblemMap.put(issue, file2ProblemList);
        }

        List<ProblemData> problemList = file2ProblemList.get(file);
        if (problemList == null) {
          problemList = new ArrayList<ProblemData>();
          file2ProblemList.put(file, problemList);
        }
        
        TextRange textRange = TextRange.EMPTY_RANGE;

        if (location != null) {
          final Position start = location.getStart();
          final Position end = location.getEnd();
          
          if (start != null && end != null && start.getOffset() <= end.getOffset()) {
            textRange = new TextRange(start.getOffset(), end.getOffset());
          }
        }
        problemList.add(new ProblemData(issue, message, textRange));
      }
    }

    @Override
    public void log(Severity severity, Throwable exception, String format, Object... args) {
      // todo: implement
    }

    @Override
    public IDomParser getDomParser() {
      return new LintCliXmlParser();
    }

    @Override
    public IJavaParser getJavaParser() {
      return new LombokParser();
    }

    @Override
    public String readFile(final File file) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

      if (vFile == null) {
        LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
        return "";
      }

      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
          if (psiFile == null) {
            LOG.info("Cannot find file " + file.getPath() + " in the PSI");
            return null;
          }
          else {
            return psiFile.getText();
          }
        }
      });
    }

    @Override
    public List<File> getJavaClassFolders(com.android.tools.lint.detector.api.Project project) {
      // todo: implement when class files checking detectors will be available
      return Collections.emptyList();
    }

    @Override
    public List<File> getJavaLibraries(com.android.tools.lint.detector.api.Project project) {
      // todo: implement
      return Collections.emptyList();
    }

    @Nullable
    private static Module findModuleForLintProject(@NotNull Project project,
                                                   @NotNull com.android.tools.lint.detector.api.Project lintProject) {
      final File dir = lintProject.getDir();
      final VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
      return vDir != null ? ModuleUtil.findModuleForFile(vDir, project) : null;
    }

    @Override
    public List<File> getJavaSourceFolders(com.android.tools.lint.detector.api.Project project) {
      final Module module = findModuleForLintProject(myProject, project);
      if (module == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      final List<File> result = new ArrayList<File>(sourceRoots.length);

      for (VirtualFile root : sourceRoots) {
        result.add(new File(root.getPath()));
      }
      return result;
    }
  }
}
