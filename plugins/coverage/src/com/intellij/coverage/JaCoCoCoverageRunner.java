/*
 * User: anna
 * Date: 20-May-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.PathUtil;
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
import java.util.Collection;

public class JaCoCoCoverageRunner extends JavaCoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + JaCoCoCoverageRunner.class.getName());

  @Override
  public ProjectData loadCoverageData(@NotNull File sessionDataFile, @Nullable CoverageSuite baseCoverageSuite) {
    final ProjectData data = new ProjectData();
    try {
      final Project project = baseCoverageSuite instanceof BaseCoverageSuite ? ((BaseCoverageSuite)baseCoverageSuite).getProject() : null;
      if (project != null) {
        loadExecutionData(sessionDataFile, data, project);
      }
    }
    catch (Exception e) {
      return data;
    }
    return data;
  }

  private static void loadExecutionData(@NotNull final File sessionDataFile, ProjectData data, @NotNull Project project) throws IOException {
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

    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null) {
        final VirtualFile[] roots = compilerModuleExtension.getOutputRoots(true);
        for (VirtualFile root : roots) {
          analyzer.analyzeAll(VfsUtil.virtualToIoFile(root));
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
          final LineData lineData = new LineData(i , desc) {
            @Override
            public int getStatus() {
              switch (methodLineStatus) {
                case ICounter.FULLY_COVERED:
                  return LineCoverage.FULL;
                case ICounter.PARTLY_COVERED:
                  return LineCoverage.PARTIAL;
                default:
                  return LineCoverage.NONE;
              }
            }
          };
          lineData.setHits(methodLineStatus == ICounter.FULLY_COVERED || methodLineStatus == ICounter.PARTLY_COVERED ? 1 : 0);
          lines[i] = lineData;
        }
      }
      classData.setLines(lines);
    }
  }


  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final SimpleJavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    StringBuffer argument = new StringBuffer("-javaagent:");
    final String agentPath = PathUtil.getJarPathForClass(org.jacoco.agent.rt.RT.class);
    final String parentPath = handleSpacesInPath(agentPath);
    argument.append(parentPath).append(File.separator).append(new File(agentPath).getName());
    argument.append("=");
    argument.append("destfile=").append(sessionDataFilePath);
    argument.append(",append=false");
    javaParameters.getVMParametersList().add(argument.toString());
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

  @Override
  public boolean isCoverageByTestApplicable() {
    return false;
  }
}
