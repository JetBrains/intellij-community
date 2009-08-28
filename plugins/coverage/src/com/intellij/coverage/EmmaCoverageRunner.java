/*
 * User: anna
 * Date: 13-Feb-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.vladium.emma.data.*;
import com.vladium.util.IntObjectMap;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class EmmaCoverageRunner extends CoverageRunner {
  private static final Logger LOG = Logger.getInstance("#" + EmmaCoverageRunner.class.getName());

  public ProjectData loadCoverageData(final File sessionDataFile) {
    ProjectData projectInfo = new ProjectData();
    try {
      final IMergeable[] data = DataFactory.load(sessionDataFile);
      IMetaData metaData = (IMetaData)data[DataFactory.TYPE_METADATA];
      ICoverageData coverageData = (ICoverageData)data[DataFactory.TYPE_COVERAGEDATA];
      if (metaData == null || coverageData == null) return projectInfo;
      final Iterator iterator = metaData.iterator();
      while (iterator.hasNext()) {
        ClassDescriptor descriptor = (ClassDescriptor)iterator.next();
        final ICoverageData.DataHolder coverage = coverageData.getCoverage(descriptor);
        if (coverage != null) {
          final String classVMName = descriptor.getClassVMName();
          final ClassData classInfo = projectInfo.getOrCreateClassData(classVMName.replace('/', '.'));
          final MethodDescriptor[] methodDescriptors = descriptor.getMethods();
          if (methodDescriptors.length > coverage.m_coverage.length) {
            LOG.info("broken data for " + classVMName + " - descriptors: " + methodDescriptors.length + "; gathered coverage: " + coverage.m_coverage.length);
            continue; //broken data
          }
          for (int i = 0; i < methodDescriptors.length; i++) {
            MethodDescriptor methodDescriptor = methodDescriptors[i];
            final boolean[] methodCoverage = coverage.m_coverage[i];
            final IntObjectMap lineMap = methodDescriptor.getLineMap();
            if (lineMap == null) continue;
            int[] lines = lineMap.keys();
            for (int line : lines) {
              final LineData lineInfo = classInfo.getOrCreateLine(line, methodDescriptor.getName() + methodDescriptor.getName());
              lineInfo.setStatus(calcStatus(methodCoverage, lineMap, lineInfo));
            }
          }
        }
      }
    }
    catch (IOException e) {
      return projectInfo;
    }
    return projectInfo;
  }

  private static int calcStatus(final boolean[] methodCoverage, final IntObjectMap lineMap, final LineData lineInfo) {
    if (methodCoverage == null) return LineCoverage.NONE;
    final int[] blocks = (int[])lineMap.get(lineInfo.getLineNumber());
    boolean isCoveredBlock = false;
    boolean isUncoveredBlock = false;
    for (int block : blocks) {
      if (methodCoverage[block]) {
        isCoveredBlock = true;
        if (isUncoveredBlock) break;
      }
      else {
        isUncoveredBlock = true;
        if (isCoveredBlock) break;
      }
    }
    if (isCoveredBlock) {
      return isUncoveredBlock ? LineCoverage.PARTIAL : LineCoverage.FULL;
    }
    else {
      return LineCoverage.NONE;
    }
  }


  public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final JavaParameters javaParameters,
                                     final boolean collectLineInfo, final boolean isSampling) {
    @NonNls StringBuffer argument = new StringBuffer("-javaagent:");
    argument.append(PathManager.getLibPath());
    argument.append(File.separator);
    argument.append("emma-agent.jar=");
    if (patterns != null && patterns.length > 0) {
      argument.append("-f ");
      for (int i = 0; i < patterns.length; i++) {
        if (i > 0) argument.append(",");
        String pattern = patterns[i];
        pattern = pattern.replace('.', '/');
        if (!pattern.endsWith("/*")) {
          //class pattern, add filter for inners
          argument.append(pattern).append(",").append(pattern).append("$*");
        } else {
          argument.append(pattern);
        }
      }
      argument.append(" ");
    }

    argument.append("-o ");
    if (sessionDataFilePath.indexOf(' ') < 0) {
      argument.append(sessionDataFilePath);
    } else {
      if (SystemInfo.isWindows) {
        argument.append("\\\"").append(sessionDataFilePath).append("\\\"");
      }
      else {
        argument.append("\"").append(sessionDataFilePath).append("\"");
      }
    }
    javaParameters.getVMParametersList().add(argument.toString());
    javaParameters.getVMParametersList().add("-Demma.rt.control=false");
  }

  public String getPresentableName() {
    return "Emma";
  }

  @NonNls
  public String getId() {
    return "emma";
  }

  @NonNls
  public String getDataFileExtension() {
    return ".es";
  }
}