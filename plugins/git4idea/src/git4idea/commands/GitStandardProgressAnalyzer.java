// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures standard remote operation notifications: "counting objects", "compressing objects", "receiving objects", "resolving deltas" and returns the fraction based on the stage of the command execution.
 * Some operations or some progress indications may be skipped - this will be handled properly.
 * @author Kirill Likhodedov
 */
public final class GitStandardProgressAnalyzer implements GitProgressAnalyzer {
  // progress of each operation is stored here. this is an overhead since operations go one by one,
  // but it looks simpler than storing current operation, checking that there was no skipped, etc.
  private final Object2DoubleOpenHashMap<Operation> myOperationsProgress = new Object2DoubleOpenHashMap<>(4);

  public static GitLineHandlerListener createListener(@NotNull final ProgressIndicator indicator) {
    final GitStandardProgressAnalyzer progressAnalyzer = new GitStandardProgressAnalyzer();
    return new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        final double fraction = progressAnalyzer.analyzeProgress(line);
        if (fraction >= 0) {
          indicator.setFraction(fraction);
          indicator.setText2(line);
        }
      }
    };
  }

  /**
   * A long git command usually consists of the operations in this enum.
   * A pattern is used to match the operation from the git output. fraction is used to indicate which part of the total git command
   * this operation takes (these numbers are not strict, we can't predict how much it will take in reality):
   * counting objects - 5%
   * compressing objects - 10 %
   * receiving objects - 80 %
   * resolving deltas - 5 %
   */
  private enum Operation {
    COUNTING_OBJECTS(".*Counting objects: +(\\d+).*", 0.05) {
      @Override
      double getProgress(int outputNumber) { // no percentage is given. +20% on each thousand objects.
        if (outputNumber > 5000) {
          return 1;
        }
        return outputNumber / 5000.0;
      }
    },
    COMPRESSING_OBJECTS(".*Compressing objects: +(\\d{1,3})%.*", 0.1),
    RECEIVING_OR_WRITING_OBJECTS(".*(?:Receiving|Writing) objects: +(\\d{1,3})%.*", 0.8), // receiving on fetch, writing on push
    RESOLVING_DELTAS(".*Resolving deltas: +(\\d{1,3})%.*", 0.05);

    private final Pattern myPattern;
    private final double myFractionInTotal;

    Operation(@NonNls String pattern, double fractionInTotal) {
      myPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
      myFractionInTotal = fractionInTotal;
    }

    /**
     * Returns the progress of the operation - the completed fraction (between 0 and 1.0) - basing on the integer given by the git
     * command output. This output number usually is a percent, but it may vary (see implementations of the method).
     */
    double getProgress(int outputNumber) {
      return outputNumber / 100.0;
    }
  }

  @Override
  public double analyzeProgress(String output) {
    for (Operation operation : Operation.values()) {
      final Matcher matcher = operation.myPattern.matcher(output);
      if (matcher.matches()) {
        try {
          double operationProgress = operation.getProgress(Integer.parseInt(matcher.group(1))); // progress of this operation
          myOperationsProgress.put(operation, operationProgress);
        }
        catch (NumberFormatException e) {
          return -1;
        }
        return updateTotalProgress(operation);
      }
    }
    return -1;
  }

  private double updateTotalProgress(Operation currentOperation) {
    // marking all operations before this one as completed
    for (Operation op : Operation.values()) {
      if (currentOperation.compareTo(op) > 0) {
        myOperationsProgress.put(op, 1.0);
      }
    }
    // counting progress
    double totalProgress = 0;
    for (Object2DoubleMap.Entry<Operation> entry : myOperationsProgress.object2DoubleEntrySet()) {
      totalProgress += entry.getKey().myFractionInTotal * entry.getDoubleValue();
    }
    return totalProgress;
  }
}
