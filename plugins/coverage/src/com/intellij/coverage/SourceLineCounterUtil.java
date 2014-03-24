package com.intellij.coverage;

import com.intellij.rt.coverage.instrumentation.SourceLineCounter;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.util.List;

/**
 * User: anna
 * Date: 12/30/11
 */
public class SourceLineCounterUtil {
  public static boolean collectNonCoveredClassInfo(final PackageAnnotator.ClassCoverageInfo classCoverageInfo,
                                                   final PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                                   byte[] content,
                                                   final boolean excludeLines) {
    if (content == null) return false;
    ClassReader reader = new ClassReader(content, 0, content.length);

    SourceLineCounter counter = new SourceLineCounter(null, excludeLines, null);
    reader.accept(counter, 0);
    classCoverageInfo.totalLineCount += counter.getNSourceLines();
    classCoverageInfo.totalMethodCount += counter.getNMethodsWithCode();
    packageCoverageInfo.totalLineCount += counter.getNSourceLines();
    packageCoverageInfo.totalMethodCount += counter.getNMethodsWithCode();
    if (!counter.isInterface()) {
      packageCoverageInfo.totalClassCount++;
    }
    return false;
  }

  public static void collectSrcLinesForUntouchedFiles(final List<Integer> uncoveredLines,
                                                      byte[] content, final boolean excludeLines) {
    final ClassReader reader = new ClassReader(content);
    final SourceLineCounter collector = new SourceLineCounter(null, excludeLines, null);
    reader.accept(collector, 0);
    final TIntObjectHashMap lines = collector.getSourceLines();
    lines.forEachKey(new TIntProcedure() {
      public boolean execute(int line) {
        line--;
        uncoveredLines.add(line);
        return true;
      }
    });
  }
}
