/*
 * User: anna
 * Date: 20-Nov-2007
 */
package com.intellij.coverage.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuiteImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.ClassFinder;
import com.intellij.rt.coverage.instrumentation.ClassPathEntry;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.util.lang.UrlClassLoader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class GenerateCoverageReportAction extends AnAction {

  private static final Logger LOG = Logger.getInstance("#" + GenerateCoverageReportAction.class.getName());

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
    final CoverageSuiteImpl currentSuite = (CoverageSuiteImpl)coverageDataManager.getCurrentSuite();

    final ExportToHTMLDialog dialog = new ExportToHTMLDialog(project, true);
    dialog.setTitle("Generate coverage report for: \'" + currentSuite.getPresentableName() + "\'");
    dialog.reset();
    dialog.show();
    if (!dialog.isOK()) return;
    dialog.apply();
    try {
      final File tempFile = File.createTempFile("temp", "");
      tempFile.deleteOnExit();
      final ProjectData projectData = currentSuite.getCoverageData(coverageDataManager);
      new SaveHook(tempFile, true, new IdeaClassFinder(project, currentSuite)).save(projectData);
      final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
      currentSuite.getRunner().generateReport(project, currentSuite.isTrackTestFolders(), tempFile.getCanonicalPath(), settings.OUTPUT_DIRECTORY, settings.OPEN_IN_BROWSER);
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
      if (projectJdk != null) {
        final CoverageSuiteImpl currentSuite = (CoverageSuiteImpl)CoverageDataManager.getInstance(project).getCurrentSuite();
        if (currentSuite != null && currentSuite.getRunner().isHTMLReportSupported()) {
          presentation.setEnabled(true);
        }
      }
    }
  }

  private static class IdeaClassFinder extends ClassFinder {
    private final Project myProject;
    private final CoverageSuiteImpl myCurrentSuite;

    public IdeaClassFinder(Project project, CoverageSuiteImpl currentSuite) {
      super(obtainPatternsFromSuite(currentSuite), new ArrayList());
      myProject = project;
      myCurrentSuite = currentSuite;
    }

    private static List<Pattern> obtainPatternsFromSuite(CoverageSuiteImpl currentSuite) {
      final List<Pattern> includePatterns = new ArrayList<Pattern>();
      for (String pattern : currentSuite.getFilteredPackageNames()) {
        includePatterns.add(Pattern.compile(pattern + ".*"));
      }

      for (String pattern : currentSuite.getFilteredClassNames()) {
        includePatterns.add(Pattern.compile(pattern));
      }
      return includePatterns;
    }

    @Override
    protected Collection getClassPathEntries() {
      final Collection entries = super.getClassPathEntries();
      final Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (Module module : modules) {
        final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
        if (extension != null) {
          final VirtualFile outputFile = extension.getCompilerOutputPath();
          try {
            if (outputFile != null) {
              final URL outputURL = VfsUtil.virtualToIoFile(outputFile).toURI().toURL();
              entries.add(new ClassPathEntry(outputFile.getPath(), new UrlClassLoader(new URL[]{outputURL}, null)));
            }
            if (myCurrentSuite.isTrackTestFolders()) {
              final VirtualFile testOutput = extension.getCompilerOutputPathForTests();
              if (testOutput != null) {
                final URL testOutputURL = VfsUtil.virtualToIoFile(testOutput).toURI().toURL();
                entries.add(new ClassPathEntry(testOutput.getPath(), new UrlClassLoader(new URL[]{testOutputURL}, null)));
              }
            }
          }
          catch (MalformedURLException e1) {
            LOG.error(e1);
          }
        }
      }
      return entries;
    }
  }
}