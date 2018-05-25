package com.intellij.coverage;

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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.rt.coverage.data.*;
import com.intellij.util.PathUtil;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ISessionInfoVisitor;
import org.jacoco.core.data.SessionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
      
        loadExecutionData(sessionDataFile, data, mainModule, project);
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
                                        @NotNull Project project) throws IOException {
    final ExecutionDataStore executionDataStore = new ExecutionDataStore();
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(sessionDataFile);
      final ExecutionDataReader executionDataReader = new ExecutionDataReader(fis);

      executionDataReader.setExecutionDataVisitor(executionDataStore);
      executionDataReader.setSessionInfoVisitor(new ISessionInfoVisitor() {
        public void visitSessionInfo(SessionInfo info) {
          System.out.println(info.toString());
        }
      });

      while (executionDataReader.read()) {
      }
    }
    finally {
      if (fis != null) {
        fis.close();
      }
    }

    final CoverageBuilder coverageBuilder = new CoverageBuilder();
    final Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);

    final Module[] modules;
    if (mainModule != null) {
      HashSet<Module> mainModuleWithDependencies = new HashSet<>();
      ReadAction.run(() -> ModuleUtilCore.getDependencies(mainModule, mainModuleWithDependencies));
      modules = mainModuleWithDependencies.toArray(Module.EMPTY_ARRAY);
    }
    else {
      modules = ModuleManager.getInstance(project).getModules();
    }
    for (Module module : modules) {
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null) {
        final String[] roots = compilerModuleExtension.getOutputRootUrls(true);
        for (String root : roots) {
          try {
            String rootPath = VfsUtilCore.urlToPath(root);
            Files.walkFileTree(Paths.get(new File(FileUtil.toSystemDependentName(rootPath)).toURI()), new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
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


  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final SimpleJavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    StringBuilder argument = new StringBuilder("-javaagent:");
    final String agentPath = handleSpacesInAgentPath(PathUtil.getJarPathForClass(RT.class));
    if (agentPath == null) return;
    argument.append(agentPath);
    argument.append("=");
    argument.append("destfile=").append(sessionDataFilePath);
    argument.append(",append=false");
    javaParameters.getVMParametersList().add(argument.toString());
  }

  @Override
  public boolean isBranchInfoAvailable(boolean sampling) {
    return true;
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
