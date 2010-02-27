/*
 * User: anna
 * Date: 16-Nov-2007
 */
package com.intellij.coverage.info;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CoberturaLoaderUtil {
  private static final Logger LOG = Logger.getInstance("#" + CoberturaLoaderUtil.class.getName());

  private CoberturaLoaderUtil() {
  }

  public static ProjectData load(final File sessionDataFile) {
    ProjectData projectInfo = new ProjectData();
    DataInputStream dataFile = null;
    try {
      dataFile = new DataInputStream(new FileInputStream(sessionDataFile));
      int classesCount = dataFile.read();
      for (int i = 0; i < classesCount; i++) {
        final String classFQName = dataFile.readUTF();
        dataFile.readUTF(); //sourcefilename
        final ClassData classData = projectInfo.getOrCreateClassData(classFQName);
        final int numberOfLines = dataFile.read();
        for (int l = 0; l < numberOfLines; l++) {
          final int lineNumber = dataFile.read();
          final LineData lineData = null; //todo classData.getOrCreateLine(lineNumber, dataFile.readUTF() + dataFile.readUTF());
          long hits = dataFile.readLong();
          final int jumpsNumber = dataFile.read();
          int trueHits = 0;
          int falseHits = 0;
          int totalHits = 0;
          for (int j = 0; j < jumpsNumber; j++) {
            dataFile.read(); //jump number
            totalHits++;
            if (dataFile.readLong() > 0) trueHits++;
            totalHits++;
            if (dataFile.readLong() > 0) falseHits++;
          }
          int defaultHitsNumber = 0;
          int branchHitNumber = 0;
          final int switchNumber = dataFile.read();
          for (int s = 0; s < switchNumber; s++) {
            dataFile.read(); //switch number
            dataFile.read(); //number of keys
            long defaultHits = dataFile.readLong();
            if (defaultHits > 0) defaultHitsNumber++;
            int coveredSwitchBranches = 0;
            final int switchBranchesNumber = dataFile.read();
            for (int b = 0; b < switchBranchesNumber; b++) {
              final long branchHit = dataFile.readLong();
              if (branchHit > 0) coveredSwitchBranches ++;
            }
            if (coveredSwitchBranches == switchBranchesNumber) branchHitNumber++;
          }
          if (hits > 0) {
            if (totalHits == trueHits + falseHits) {
              if (defaultHitsNumber == switchNumber && branchHitNumber == switchNumber) {
                lineData.setStatus(LineCoverage.FULL);
                continue;
              }
            }
            lineData.setStatus(LineCoverage.PARTIAL);
          } else {
            lineData.setStatus(LineCoverage.NONE);
          }
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    finally {
      if (dataFile != null) {
        try {
          dataFile.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return projectInfo;
  }

  /*public static List<TraceInfo> loadTestLines(final File testSessionFile, ProjectInfo projectInfo) {
    final List<TraceInfo> result = new ArrayList<TraceInfo>();
    DataInputStream dataFile = null;
    try {
      dataFile = new DataInputStream(new FileInputStream(testSessionFile));
      final int count = dataFile.read();
      for (int t = 0; t < count; t ++) {
        final String className = dataFile.readUTF();
        final int lineNumber = dataFile.read();
        final ClassInfo classInfo = projectInfo.getClassInfo(className);
        if (classInfo != null) {
          final LineInfo lineInfo = classInfo.getLineInfo(lineNumber);
          if (lineInfo != null) {
            result.add(new TraceInfo(classInfo, lineInfo));
          }
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    finally {
      if (dataFile != null) {
        try {
          dataFile.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    return result;
  }*/
}