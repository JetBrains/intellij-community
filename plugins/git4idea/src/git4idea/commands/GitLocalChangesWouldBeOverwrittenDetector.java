// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Detects the error which happens, when some untracked working tree files prevent merge or checkout.</p>
 *
 * <p>Using a separate detector class instead of defining just an {@link GitMessageWithFilesDetector.Event},
 * because older versions of Git have other format of displaying this error that doesn't match any existing detectors.</p>
 *
 * @author Kirill Likhodedov
 */
public class GitLocalChangesWouldBeOverwrittenDetector extends GitMessageWithFilesDetector {

  private static final Pattern OLD_CHECKOUT_PATTERN = Pattern.compile(
    ".*You have local changes to '(.*)'; cannot switch branches.*"
  );

  private static final Pattern OLD_MERGE_PATTERN = Pattern.compile(
    ".*Your local changes to '(.*)' would be overwritten by merge.*"
  );

  private static final Pattern[] RESET_PATTERNS = new Pattern[]{
    Pattern.compile(
      ".*Entry '(.*)' not uptodate. Cannot merge.*"
    ),
    Pattern.compile(
      ".*Entry '(.*)' would be overwritten by merge.*"
    )};

  // common for checkout and merge
  public static final Event NEW_PATTERN = new Event(
    "LocalChangesDetector",
    List.of(Pattern.compile(".*Your local changes to the following files would be overwritten by.*")),
    List.of(Pattern.compile(".*commit your changes or stash them before.*"),
            Pattern.compile(".*Merge with strategy .* failed.*"),
            Pattern.compile(".*No merge strategy handled the merge.*"))
  );

  private final @NotNull Operation myOperation;

  private final @NotNull Set<String> myAffectedFilesRawLines = new HashSet<>();

  public enum Operation {
    CHECKOUT(OLD_CHECKOUT_PATTERN),
    MERGE(OLD_MERGE_PATTERN),
    RESET(RESET_PATTERNS);

    private final Pattern @NotNull [] myPatterns;

    Operation(Pattern @NotNull ... patterns) {
      myPatterns = patterns;
    }

    Pattern @NotNull [] getPatterns() {
      return myPatterns;
    }
  }

  public GitLocalChangesWouldBeOverwrittenDetector(@NotNull VirtualFile root, @NotNull Operation operation) {
    super(NEW_PATTERN, root);
    myOperation = operation;
  }

  @Override
  public @NotNull Set<String> getRelativeFilePaths() {
    // In some cases the error message is not formatted as 1 line per 1 file name.
    // The line also starts with 2 spaces instead of a tab and files are space-separated.
    if (myAffectedFiles.size() == 1) {
      String onlyLine = ContainerUtil.getOnlyItem(myAffectedFilesRawLines);
      // E.g., "  file1 dir/file2 dir/subdir/file3"
      if (onlyLine != null && onlyLine.matches("^ {2}([^ ].*)")) {
        myAffectedFiles.clear();
        myAffectedFilesRawLines.clear();
        myAffectedFiles.addAll(Arrays.asList(onlyLine.trim().split(" ")));
      }
    }

    return super.getRelativeFilePaths();
  }

  @Override
  protected void handleAsAffectedFiles(@NotNull String line) {
    myAffectedFilesRawLines.add(line);
    super.handleAsAffectedFiles(line);
  }

  @Override
  public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
    if (myOperation == Operation.MERGE && myMessageOutputType != null) {
      // In case of merge the error message is not necessary formatted as 1 line per 1 file name
      // For instance:
      // error: Your local changes to the following files would be overwritten by merge:
      // <file1> <file2>
      // <stdin>:7: trailing whitespace.
      // ...
      if (line.matches("^<stdin>:\\d+:.*") && myAffectedFiles.size() == 1) {
        // Ignore further lines
        myMessageOutputType = null;
      }
    }
    super.onLineAvailable(line, outputType);
    for (Pattern pattern : myOperation.getPatterns()) {
      Matcher m = pattern.matcher(line);
      if (m.matches()) {
        myMessageDetected = true;
        myAffectedFiles.add(m.group(1));
        break;
      }
    }
  }
}
