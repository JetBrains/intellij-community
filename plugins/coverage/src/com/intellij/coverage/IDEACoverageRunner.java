/*
 * User: anna
 * Date: 20-May-2008
 */
package com.intellij.coverage;

import com.intellij.CommonBundle;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import com.intellij.util.PathUtil;
import jetbrains.coverage.report.ClassInfo;
import jetbrains.coverage.report.ReportBuilderFactory;
import jetbrains.coverage.report.ReportGenerationFailedException;
import jetbrains.coverage.report.SourceCodeProvider;
import jetbrains.coverage.report.html.HTMLReportBuilder;
import jetbrains.coverage.report.idea.IDEACoverageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class IDEACoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + IDEACoverageRunner.class.getName());

  public ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite coverageSuite) {
    return ProjectDataLoader.load(sessionDataFile);
  }

  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final SimpleJavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    StringBuffer argument = new StringBuffer("-javaagent:");
    argument.append(PathUtil.getJarPathForClass(ProjectData.class));
    argument.append("=");
    if (SystemInfo.isWindows) {
      argument.append("\\\"").append(sessionDataFilePath).append("\\\"");
    }
    else {
      argument.append(sessionDataFilePath);
    }
    argument.append(" ").append(String.valueOf(collectLineInfo));
    argument.append(" ").append(Boolean.FALSE.toString()); //append unloaded
    argument.append(" ").append(Boolean.FALSE.toString()); //merge with existing
    argument.append(" ").append(String.valueOf(isSampling));
    if (patterns != null && patterns.length > 0) {
      argument.append(" ");
      for (String coveragePattern : patterns) {
        coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
        if (!coveragePattern.endsWith(".*")) { //include inner classes
          coveragePattern += "(\\$.*)*";
        }
        argument.append(coveragePattern).append(" ");
      }
    }
    javaParameters.getVMParametersList().add(argument.toString());
  }

  @Override
  public boolean isHTMLReportSupported() {
    return true;
  }

  @Override
  public void generateJavaReport(@NotNull final Project project,
                                 final boolean trackTestFolders,
                                 @NotNull final String coverageDataFileName,
                                 final String outputDir,
                                 final boolean openInBrowser) {

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating coverage report ...") {
      final Exception[] myExceptions = new Exception[1];


      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          final HTMLReportBuilder builder = ReportBuilderFactory.createHTMLReportBuilder();
          builder.setReportDir(new File(outputDir));
          builder.generateReport(new IDEACoverageData(coverageDataFileName, new SourceCodeProvider() {
            public String getSourceCode(@NotNull final String classname) throws IOException {
              return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                public String compute() {
                  final PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), classname);
                  return psiClass != null ? psiClass.getContainingFile().getText() : "";
                }
              });
            }
          }) {
            @NotNull
            @Override
            public Collection<ClassInfo> getClasses() {
              final Collection<ClassInfo> classes = super.getClasses();
              if (!trackTestFolders) {
                final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                final GlobalSearchScope productionScope = GlobalSearchScope.projectProductionScope(project);
                for (Iterator<ClassInfo> iterator = classes.iterator(); iterator.hasNext();) {
                  final ClassInfo aClass = iterator.next();
                  final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
                    public PsiClass compute() {
                      return psiFacade.findClass(aClass.getFQName(), productionScope);
                    }
                  });
                  if (psiClass == null) {
                    iterator.remove();
                  }
                }
              }
              return classes;
            }
          });

        }
        catch (IOException e) {
          LOG.error(e);
        }
        catch (ReportGenerationFailedException e) {
          myExceptions[0] = e;
        }
      }

      @Override
      public void onSuccess() {
        if (myExceptions[0] != null) {
          Messages.showErrorDialog(project, myExceptions[0].getMessage(), CommonBundle.getErrorTitle());
          return;
        }
        if (openInBrowser) BrowserUtil.launchBrowser(VfsUtil.pathToUrl(outputDir + "/index.html"));
      }
    });
  }

  public String getPresentableName() {
    return "IDEA";
  }

  public String getId() {
    return "idea";
  }

  public String getDataFileExtension() {
    return "ic";
  }

  @Override
  public boolean isCoverageByTestApplicable() {
    return true;
  }
}