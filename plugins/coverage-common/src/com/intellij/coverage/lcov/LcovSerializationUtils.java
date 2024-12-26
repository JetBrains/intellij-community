// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.lcov;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LcovSerializationUtils {
  private static final String SOURCE_FILE_PREFIX = "SF:";
  private static final String LINE_HIT_PREFIX = "DA:";
  private static final String FUNCTION_PREFIX = "FN:";
  private static final String END_OF_RECORD = "end_of_record";
  private static final Pattern FN_PATTERN = Pattern.compile("^FN:(\\d+),(.+)$");

  public static @NotNull LcovCoverageReport readLCOV(@NotNull List<File> lcovFiles) throws IOException {
    LcovCoverageReport report = new LcovCoverageReport();
    for (File lcovFile : lcovFiles) {
      try (BufferedReader reader = new BufferedReader(new FileReader(lcovFile, StandardCharsets.UTF_8))) {
        String currentFileName = null;
        String currentFunction = null;
        List<LcovCoverageReport.LineHits> lineDataList = null;
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith(SOURCE_FILE_PREFIX)) {
            currentFileName = line.substring(SOURCE_FILE_PREFIX.length());
            currentFunction = null;
            lineDataList = new ArrayList<>();
          }
          else if (line.startsWith(FUNCTION_PREFIX)) {
            Matcher fnMatcher = FN_PATTERN.matcher(line);
            if (fnMatcher.find()) {
              currentFunction = fnMatcher.group(2);
            }
          }
          else if (line.startsWith(LINE_HIT_PREFIX)) {
            if (lineDataList == null) {
              throw new RuntimeException("lineDataList is null!");
            }
            String[] values = line.substring(LINE_HIT_PREFIX.length()).split(",");
            int lineNum = Integer.parseInt(values[0]);
            int hitCount = Integer.parseInt(values[1]);
            LcovCoverageReport.LineHits lineHits = new LcovCoverageReport.LineHits(lineNum, hitCount, currentFunction);
            lineDataList.add(lineHits);
          }
          else if (END_OF_RECORD.equals(line)) {
            if (lineDataList == null) {
              throw new RuntimeException("lineDataList is null!");
            }
            report.mergeFileReport(normalizeFilePath(currentFileName), lineDataList);
            currentFileName = null;
            lineDataList = null;
          }
        }
      }
    }
    return report;
  }

  public static ProjectData convertToProjectData(@NotNull LcovCoverageReport report,
                                                 @NotNull Function<? super String, String> toLocalPathConvertor) {
    ProjectData projectData = new ProjectData();
    for (Map.Entry<String, List<LcovCoverageReport.LineHits>> entry : report.getInfo().entrySet()) {
      String filePath = entry.getKey();
      String localPath = toLocalPathConvertor.apply(filePath);
      ClassData classData = projectData.getOrCreateClassData(normalizeFilePath(localPath));
      int max = 0;
      List<LcovCoverageReport.LineHits> lineHitsList = entry.getValue();
      if (!lineHitsList.isEmpty()) {
        LcovCoverageReport.LineHits lastLineHits = lineHitsList.get(lineHitsList.size() - 1);
        max = lastLineHits.getLineNumber();
      }
      LineData[] lines = new LineData[max + 1];
      for (LcovCoverageReport.LineHits lineHits : lineHitsList) {
        LineData lineData = new LineData(lineHits.getLineNumber(), lineHits.getFunctionName());
        lineData.setHits(lineHits.getHits());
        lines[lineHits.getLineNumber()] = lineData;
      }
      classData.setLines(lines);
    }
    return projectData;
  }

  private static @NotNull String normalizeFilePath(@NotNull String filePath) {
    if (SystemInfo.isWindows) {
      filePath = StringUtil.toLowerCase(filePath);
    }
    return FileUtil.toSystemIndependentName(filePath);
  }
}
