package com.intellij.coverage;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.*;
import com.intellij.util.PathUtil;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.analysis.*;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;

public class JaCoCoCoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance(JaCoCoCoverageRunner.class);

  @Override
  public ProjectData loadCoverageData(@NotNull File sessionDataFile, @Nullable CoverageSuite baseCoverageSuite) {
    final ProjectData data = new ProjectData();
    try {
      final Project project = baseCoverageSuite instanceof BaseCoverageSuite ? baseCoverageSuite.getProject() : null;
      if (project != null) {
        RunConfigurationBase configuration = ((BaseCoverageSuite)baseCoverageSuite).getConfiguration();
        
        Module mainModule = configuration instanceof ModuleBasedConfiguration 
                            ? ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule() 
                            : null;
      
        loadExecutionData(sessionDataFile, data, mainModule, project, baseCoverageSuite);
      }
    }
    catch (Exception e) {
      LOG.error(e);
      return data;
    }
    return data;
  }

  private static void loadExecutionData(@NotNull final File sessionDataFile,
                                        ProjectData data,
                                        @Nullable Module mainModule,
                                        @NotNull Project project, 
                                        CoverageSuite suite) throws IOException {
    ExecFileLoader loader = new ExecFileLoader();
    final CoverageBuilder coverageBuilder = getCoverageBuilder(sessionDataFile, mainModule, project, loader, (JavaCoverageSuite)suite);

    for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
      String className = classCoverage.getName();
      className = className.replace('\\', '.').replace('/', '.');
      final ClassData classData = data.getOrCreateClassData(className);
      final Collection<IMethodCoverage> methods = classCoverage.getMethods();
      LineData[] lines = new LineData[classCoverage.getLastLine() + 1];
      for (IMethodCoverage method : methods) {
        final String desc = method.getName() + method.getDesc();
        // Line numbers are 1-based here.
        final int firstLine = method.getFirstLine();
        final int lastLine = method.getLastLine();
        for (int i = firstLine; i <= lastLine; i++) {
          final ILine methodLine = method.getLine(i);
          final int methodLineStatus = methodLine.getStatus();
          if (methodLineStatus == ICounter.EMPTY) continue;
          final LineData lineData = new LineData(i , desc);
          switch (methodLineStatus) {
            case ICounter.FULLY_COVERED:
              lineData.setStatus(LineCoverage.FULL);
            case ICounter.PARTLY_COVERED:
              lineData.setStatus(LineCoverage.PARTIAL);
            default:
              lineData.setStatus(LineCoverage.NONE);
          }

          lineData.setHits(methodLineStatus == ICounter.FULLY_COVERED || methodLineStatus == ICounter.PARTLY_COVERED ? 1 : 0);
          ICounter branchCounter = methodLine.getBranchCounter();
          int coveredCount = branchCounter.getCoveredCount();
          for (int b = 0; b < branchCounter.getTotalCount(); b++) {
            JumpData jump = lineData.addJump(b);
            if (coveredCount-- > 0) {
              jump.setTrueHits(1);
              jump.setFalseHits(1);
            }
          }

          classData.registerMethodSignature(lineData);
          lineData.fillArrays();
          lines[i] = lineData;
        }
      }
      classData.setLines(lines);
    }
  }

  private static CoverageBuilder getCoverageBuilder(@NotNull File sessionDataFile,
                                                    @Nullable Module mainModule,
                                                    @NotNull Project project,
                                                    ExecFileLoader loader, 
                                                    JavaCoverageSuite suite) throws IOException {
    loader.load(sessionDataFile);

    final CoverageBuilder coverageBuilder = new CoverageBuilder();
    final Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);

    final Module[] modules = getModules(mainModule, project);
    for (Module module : modules) {
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null) {
        final String[] roots = compilerModuleExtension.getOutputRootUrls(true);
        for (String root : roots) {
          try {
            Path rootPath = Paths.get(new File(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(root))).toURI());
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String vmClassName = rootPath.relativize(path).toString().replaceAll(StringUtil.escapeToRegexp(File.separator), ".");
                if (suite.isClassFiltered(vmClassName, suite.getExcludedClassNames()) ||
                    !suite.isPackageFiltered(StringUtil.getPackageName(vmClassName))) {
                  return FileVisitResult.CONTINUE;
                }
                File file = path.toFile();
                try {
                  analyzer.analyzeAll(file);
                }
                catch (Exception e) {
                  LOG.info(e);
                }
                return FileVisitResult.CONTINUE;
              }
            });
          }
          catch (NoSuchFileException ignore) {}
        }
      }
    }
    return coverageBuilder;
  }

  private static Module[] getModules(@Nullable Module mainModule,
                                     @NotNull Project project) {
    final Module[] modules;
    if (mainModule != null) {
      HashSet<Module> mainModuleWithDependencies = new HashSet<>();
      ReadAction.run(() -> ModuleUtilCore.getDependencies(mainModule, mainModuleWithDependencies));
      modules = mainModuleWithDependencies.toArray(Module.EMPTY_ARRAY);
    }
    else {
      modules = ModuleManager.getInstance(project).getModules();
    }
    return modules;
  }


  @Override
  public void appendCoverageArgument(String sessionDataFilePath,
                                     @Nullable String[] patterns,
                                     SimpleJavaParameters parameters,
                                     boolean collectLineInfo,
                                     boolean isSampling) {
    appendCoverageArgument(sessionDataFilePath, patterns, null, parameters, collectLineInfo, isSampling, null);
  }

  @Override
  public void appendCoverageArgument(String sessionDataFilePath,
                                     @Nullable String[] patterns,
                                     String[] excludePatterns,
                                     SimpleJavaParameters javaParameters,
                                     boolean collectLineInfo,
                                     boolean isSampling,
                                     String sourceMapPath) {
    StringBuilder argument = new StringBuilder("-javaagent:");
    final String agentPath = handleSpacesInAgentPath(PathUtil.getJarPathForClass(RT.class));
    if (agentPath == null) return;
    argument.append(agentPath);
    argument.append("=");
    argument.append("destfile=").append(sessionDataFilePath);
    argument.append(",append=false");
    if (patterns != null) {
      argument.append(",includes=").append(StringUtil.join(patterns, ":"));
    }
    if (excludePatterns != null) {
      argument.append(",excludes=").append(StringUtil.join(excludePatterns, ":"));
    }
    javaParameters.getVMParametersList().add(argument.toString());
  }

  @Override
  public boolean isBranchInfoAvailable(boolean sampling) {
    return true;
  }

  @Override
  public void generateReport(CoverageSuitesBundle suite, Project project) throws IOException {
    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    File targetDirectory = new File(settings.OUTPUT_DIRECTORY);
    File coverageFile = new File(suite.getSuites()[0].getCoverageDataFileName());
    RunConfigurationBase runConfiguration = suite.getRunConfiguration();
    Module module = runConfiguration instanceof ModuleBasedConfiguration 
                    ? ((ModuleBasedConfiguration)runConfiguration).getConfigurationModule().getModule() 
                    : null;

    ExecFileLoader loader = new ExecFileLoader();
    CoverageBuilder coverageBuilder = getCoverageBuilder(coverageFile, module, project, loader, (JavaCoverageSuite)suite.getSuites()[0]);

    final IBundleCoverage bundleCoverage = coverageBuilder.getBundle(coverageFile.getName());

    final IReportVisitor visitor = new HTMLFormatter().createVisitor(new FileMultiReportOutput(targetDirectory));

    visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                      loader.getExecutionDataStore().getContents());

    int tabWidth = 4;
    MultiSourceFileLocator multiSourceFileLocator = new MultiSourceFileLocator(tabWidth);
    for (Module srcModule : getModules(module, project)) {
      VirtualFile[] roots = ModuleRootManager.getInstance(srcModule).getSourceRoots(true);
      for (VirtualFile root : roots) {
        multiSourceFileLocator.add(new DirectorySourceFileLocator(VfsUtilCore.virtualToIoFile(root), StandardCharsets.UTF_8.name(), tabWidth));
      }
    }
    visitor.visitBundle(bundleCoverage, multiSourceFileLocator);
    visitor.visitEnd();
  }

  public String getPresentableName() {
    return "JaCoCo";
  }

  public String getId() {
    return "jacoco";
  }

  public String getDataFileExtension() {
    return "exec";
  }
}
