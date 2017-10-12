// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import git4idea.GitFormatException;
import git4idea.GitVcs;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitLogParser {
  private static final Logger LOG = Logger.getInstance(GitLogParser.class);
  
  // Single records begin with %x01, end with %03. Items of commit information (hash, committer, subject, etc.) are separated by %x02.
  // each character is declared twice - for Git pattern format and for actual character in the output.
  public static final String RECORD_START = "\u0001\u0001";
  public static final char ITEMS_SEPARATOR = '\u0002';
  public static final String RECORD_END = "\u0003\u0003";
  public static final String RECORD_START_GIT = "%x01%x01";
  private static final String ITEMS_SEPARATOR_GIT = "%x02";
  private static final String RECORD_END_GIT = "%x03%x03";
  private static final int INPUT_ERROR_MESSAGE_HEAD_LIMIT = 1000000; // limit the string by ~2mb
  private static final int INPUT_ERROR_MESSAGE_TAIL_LIMIT = 100;

  @NotNull private final GitLogOption[] myOptions;
  private final boolean mySupportsRawBody;
  @NotNull private final NameStatus myNameStatusOption;
  @NotNull private final String myPretty;

  @NotNull private OptionsParser myOptionsParser = new OptionsParser();
  @NotNull private PathsParser myPathsParser = new PathsParser();

  private boolean myIsInBody = true;

  private GitLogParser(boolean supportsRawBody,
                       @NotNull NameStatus option,
                       @NotNull GitLogOption... options) {
    myPretty = "--pretty=format:" + makeFormatFromOptions(options);
    myNameStatusOption = option;
    myOptions = options;
    mySupportsRawBody = supportsRawBody;
  }

  public GitLogParser(@NotNull Project project,
                      @NotNull NameStatus nameStatus,
                      @NotNull GitLogOption... options) {
    this(GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(GitVcs.getInstance(project).getVersion()), nameStatus, options);
  }

  public GitLogParser(@NotNull Project project,
                      @NotNull GitLogOption... options) {
    this(project, NameStatus.NONE, options);
  }

  @NotNull
  public List<GitLogRecord> parse(@NotNull CharSequence output) {
    List<GitLogRecord> result = ContainerUtil.newArrayList();

    List<CharSequence> lines = StringUtil.split(output, "\n", true, false);
    for (CharSequence line : lines) {
      try {
        result.addAll(parseLine(line));
      }
      catch (GitFormatException e) {
        clear();
        LOG.error(e);
      }
    }

    GitLogRecord record = finish();
    if (record != null) result.add(record);

    return result;
  }

  @Nullable
  public GitLogRecord parseOneRecord(@NotNull CharSequence output) {
    List<GitLogRecord> records = parse(output);
    clear();
    if (records.isEmpty()) return null;
    return ContainerUtil.getFirstItem(records);
  }

  @NotNull
  public List<GitLogRecord> parseLine(@NotNull CharSequence line) {
    if (myIsInBody) {
      if (myOptionsParser.parseLine(line)) {
        myIsInBody = false;
      }
    }
    else {
      if (CharArrayUtil.regionMatches(line, 0, RECORD_START)) {
        myIsInBody = true;
        return ContainerUtil.concat(Collections.singletonList(createRecord()), parseLine(line));
      }

      myPathsParser.parseLine(line);
    }

    return ContainerUtil.emptyList();
  }

  @Nullable
  public GitLogRecord finish() {
    if (myOptionsParser.isEmpty()) return null;
    return createRecord();
  }

  @NotNull
  private GitLogRecord createRecord() {
    List<String> options = myOptionsParser.getResult();
    myOptionsParser.clear();

    Pair<List<String>, List<GitLogStatusInfo>> result = myPathsParser.getResult();
    myPathsParser.clear();

    return new GitLogRecord(createOptions(options), result.first, result.second, mySupportsRawBody);
  }

  public void clear() {
    myOptionsParser.clear();
    myPathsParser.clear();
    myIsInBody = true;
  }

  @NotNull
  private Map<GitLogOption, String> createOptions(@NotNull List<String> options) {
    Map<GitLogOption, String> optionsMap = new HashMap<>(options.size());
    int index = 0;
    for (String value : options) {
      if (index >= myOptions.length) {
        break;
      }
      optionsMap.put(myOptions[index], value);
      index++;
    }
    for (; index < myOptions.length; index++) {
      optionsMap.put(myOptions[index], "");
    }
    return optionsMap;
  }

  @NotNull
  public String getPretty() {
    return myPretty;
  }

  @NotNull
  private static String makeFormatFromOptions(@NotNull GitLogOption[] options) {
    Function<GitLogOption, String> function = option -> "%" + option.getPlaceholder();
    return RECORD_START_GIT + StringUtil.join(options, function, ITEMS_SEPARATOR_GIT) + RECORD_END_GIT;
  }

  private static void throwGFE(@NotNull String message, @NotNull CharSequence line) {
    throw new GitFormatException(message + " [" + getTruncatedEscapedOutput(line) + "]");
  }

  @NotNull
  private static String getTruncatedEscapedOutput(@NotNull CharSequence line) {
    String lineString;

    String formatString = "%s...(%d more characters)...%s";
    if (line.length() > INPUT_ERROR_MESSAGE_HEAD_LIMIT + INPUT_ERROR_MESSAGE_TAIL_LIMIT + formatString.length()) {
      lineString = String.format(formatString, line.subSequence(0, INPUT_ERROR_MESSAGE_HEAD_LIMIT),
                                 (line.length() - INPUT_ERROR_MESSAGE_HEAD_LIMIT - INPUT_ERROR_MESSAGE_TAIL_LIMIT),
                                 line.subSequence(line.length() - INPUT_ERROR_MESSAGE_TAIL_LIMIT, line.length()));
    }
    else {
      lineString = line.toString();
    }

    return StringUtil.escapeStringCharacters(lineString);
  }

  /**
   * Record format:
   * <p>One git log record.
   * RECORD_START - optional: it is split out when calling parse() but it is not when calling parseOneRecord() directly.
   * commit information separated by ITEMS_SEPARATOR.
   * RECORD_END
   * Optionally: changed paths or paths with statuses (if --name-only or --name-status options are given).</p>
   * <p>Example:
   * <pre>
   * 2c815939f45fbcfda9583f84b14fe9d393ada790&lt;ITEM_SEPARATOR&gt;sample commit&lt;RECORD_END&gt;
   * D       a.txt</pre></p>
   */

  // --name-only, --name-status or no flag
  enum NameStatus {
    /**
     * No flag.
     */
    NONE,
    /**
     * --name-only
     */
    NAME,
    /**
     * --name-status
     */
    STATUS
  }

  /**
   * Options which may be passed to 'git log --pretty=format:' as placeholders and then parsed from the result.
   * These are the pieces of information about a commit which we want to get from 'git log'.
   */
  enum GitLogOption {
    HASH("H"), TREE("T"), COMMIT_TIME("ct"), AUTHOR_NAME("an"), AUTHOR_TIME("at"), AUTHOR_EMAIL("ae"), COMMITTER_NAME("cn"),
    COMMITTER_EMAIL("ce"), SUBJECT("s"), BODY("b"), PARENTS("P"), REF_NAMES("d"), SHORT_REF_LOG_SELECTOR("gd"),
    RAW_BODY("B");

    private String myPlaceholder;

    GitLogOption(String placeholder) {
      myPlaceholder = placeholder;
    }

    private String getPlaceholder() {
      return myPlaceholder;
    }
  }

  private static class OptionsParser {
    @NotNull private final PartialResult myResult = new PartialResult();

    public boolean parseLine(@NotNull CharSequence line) {
      int offset = 0;

      if (CharArrayUtil.regionMatches(line, offset, RECORD_START)) {
        offset += RECORD_START.length();
      }

      while (offset < line.length()) {
        if (offset == line.length() - RECORD_END.length() && CharArrayUtil.regionMatches(line, offset, RECORD_END)) {
          myResult.finishItem();
          return true;
        }

        char c = line.charAt(offset);
        if (c == ITEMS_SEPARATOR) {
          myResult.finishItem();
        }
        else {
          myResult.append(c);
        }
        offset++;
      }

      myResult.append('\n');

      return false;
    }

    @NotNull
    public List<String> getResult() {
      return myResult.getResult();
    }

    public void clear() {
      myResult.clear();
    }

    public boolean isEmpty() {
      return myResult.isEmpty();
    }
  }

  private class PathsParser {
    @NotNull private List<String> myPaths = ContainerUtil.newArrayList();
    @NotNull private List<GitLogStatusInfo> myStatuses = ContainerUtil.newArrayList();

    public void parseLine(@NotNull CharSequence line) {
      if (line.length() == 0) return;

      List<String> match = parsePathsLine(line);

      if (match.isEmpty()) {
        // ignore
      }
      else {
        if (myNameStatusOption == NameStatus.NAME) {
          if (match.size() > 2) {
            throwGFE("Paths list " + match + " does not match", line);
          }

          myPaths.add(match.get(0));
          if (match.size() == 2) {
            myPaths.add(match.get(1));
          }
        }
        else {
          if (myNameStatusOption != NameStatus.STATUS) throwGFE("Status list not expected", line);

          if (match.size() == 2) {
            myStatuses.add(new GitLogStatusInfo(GitChangeType.fromString(match.get(0)), match.get(1), null));
          }
          else if (match.size() == 3) {
            myStatuses.add(new GitLogStatusInfo(GitChangeType.fromString(match.get(0)), match.get(1), match.get(2)));
          }
          else {
            throwGFE("Status list " + match + " does not match", line);
          }
        }
      }
    }

    @NotNull
    private List<String> parsePathsLine(@NotNull CharSequence line) {
      int offset = 0;

      PartialResult result = new PartialResult();
      while (offset < line.length()) {
        if (atLineEnd(line, offset)) {
          break;
        }

        char charAt = line.charAt(offset);
        if (charAt == '\t') {
          result.finishItem();
        }
        else {
          result.append(charAt);
        }

        offset++;
      }

      result.finishItem();
      return result.getResult();
    }

    private boolean atLineEnd(@NotNull CharSequence line, int offset) {
      while (offset < line.length() && (line.charAt(offset) == '\t' || line.charAt(offset) == ' ')) offset++;
      if (offset == line.length() || (line.charAt(offset) == '\n' || line.charAt(offset) == '\r')) return true;
      return false;
    }

    @NotNull
    public Pair<List<String>, List<GitLogStatusInfo>> getResult() {
      return Pair.create(myPaths, myStatuses);
    }

    public void clear() {
      myPaths = ContainerUtil.newArrayList();
      myStatuses = ContainerUtil.newArrayList();
    }
  }

  private static class PartialResult {
    @NotNull private List<String> myResult = ContainerUtil.newArrayList();
    @NotNull private final StringBuilder myCurrentItem = new StringBuilder();

    public void append(char c) {
      myCurrentItem.append(c);
    }

    public void finishItem() {
      myResult.add(myCurrentItem.toString());
      myCurrentItem.setLength(0);
    }

    @NotNull
    public List<String> getResult() {
      return myResult;
    }

    public void clear() {
      myCurrentItem.setLength(0);
      myResult = ContainerUtil.newArrayList();
    }

    public boolean isEmpty() {
      return myResult.isEmpty() && myCurrentItem.length() == 0;
    }
  }
}
