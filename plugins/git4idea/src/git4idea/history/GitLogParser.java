// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import git4idea.GitFormatException;
import git4idea.GitVcs;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Parses the 'git log' output basing on the given number of options.
 * Doesn't execute of prepare the command itself, performs only parsing.</p>
 * <p>
 * Usage:<ol>
 * <li> Pass options you want to have in the output to the constructor using the {@link GitLogOption} enum constants.
 * <li> Get the custom format pattern for 'git log' by calling {@link #getPretty()}
 * <li> Call the command and retrieve the output.
 * <li> Parse the output via {@link #parseLine(CharSequence)}(prefered), {@link #parse(CharSequence)} or {@link #parseOneRecord(CharSequence)}.
 * Note that {@link #parseLine(CharSequence)} expects lines without line separators</ol></p>
 * <p>Note that you may pass one set of options to the GitLogParser constructor and then execute git log with other set of options.
 * In that case {@link #parse(CharSequence)} will likely fail with {@link GitFormatException}.
 * Moreover you really <b>must</b> use {@link #getPretty()} to pass "--pretty=format" pattern to 'git log' -- otherwise the parser won't be able
 * to parse output of 'git log' (because special separator characters are used for that).</p>
 * <p>Commit records have the following format:
 * <pre>
 * RECORD_START (commit information, separated by ITEMS_SEPARATOR) RECORD_END \n (changed paths with statuses)?</pre>
 * Example:
 * <pre>
 * 2c815939f45fbcfda9583f84b14fe9d393ada790&lt;ITEMS_SEPARATOR&gt;sample commit&lt;RECORD_END&gt;
 * D       a.txt</pre></p>
 *
 * @see GitLogRecord
 */
public class GitLogParser {
  private static final Logger LOG = Logger.getInstance(GitLogParser.class);

  // Single records begin with %x01%x01, end with %03%03. Items of commit information (hash, committer, subject, etc.) are separated by %x02%x02.
  static final String RECORD_START = "\u0001\u0001";
  static final String ITEMS_SEPARATOR = "\u0002\u0002";
  static final String RECORD_END = "\u0003\u0003";
  private static final int MAX_SEPARATOR_LENGTH = 10;
  private static final char[] CONTROL_CHARS = new char[]{'\u0001', '\u0002', '\u0003'};
  private static final int INPUT_ERROR_MESSAGE_HEAD_LIMIT = 1000000; // limit the string by ~2mb
  private static final int INPUT_ERROR_MESSAGE_TAIL_LIMIT = 100;

  private static final AtomicInteger ERROR_COUNT = new AtomicInteger();

  private final boolean mySupportsRawBody;
  @NotNull private final String myPretty;

  @NotNull private final OptionsParser myOptionsParser;
  @NotNull private final PathsParser myPathsParser;

  private final String myRecordStart;
  private final String myRecordEnd;
  private final String myItemsSeparator;

  private boolean myIsInBody = true;

  private GitLogParser(boolean supportsRawBody,
                       @NotNull NameStatus nameStatusOption,
                       @NotNull GitLogOption... options) {
    mySupportsRawBody = supportsRawBody;

    myRecordStart = RECORD_START + generateRandomSequence();
    myRecordEnd = RECORD_END + generateRandomSequence();
    myItemsSeparator = ITEMS_SEPARATOR + generateRandomSequence();

    myPretty = "--pretty=format:" + makeFormatFromOptions(options);

    myOptionsParser = new OptionsParser(options);
    myPathsParser = new PathsParser(nameStatusOption);
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
        GitLogRecord record = parseLine(line);
        if (record != null) {
          result.add(record);
        }
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

  /**
   * Expects a line without separator.
   */
  @Nullable
  public GitLogRecord parseLine(@NotNull CharSequence line) {
    if (myPathsParser.expectsPaths()) {
      return parseLineWithPaths(line);
    }
    return parseLineWithoutPaths(line);
  }

  @Nullable
  private GitLogRecord parseLineWithPaths(@NotNull CharSequence line) {
    if (myIsInBody) {
      myIsInBody = !myOptionsParser.parseLine(line);
    }
    else {
      if (CharArrayUtil.regionMatches(line, 0, myRecordStart)) {
        GitLogRecord record = createRecord();
        myIsInBody = !myOptionsParser.parseLine(line);
        return record;
      }

      myPathsParser.parseLine(line);
    }

    return null;
  }

  @Nullable
  private GitLogRecord parseLineWithoutPaths(@NotNull CharSequence line) {
    if (myOptionsParser.parseLine(line)) {
      return createRecord();
    }
    return null;
  }

  @Nullable
  public GitLogRecord finish() {
    if (myOptionsParser.isEmpty()) return null;
    return createRecord();
  }

  @NotNull
  private GitLogRecord createRecord() {
    Map<GitLogOption, String> options = myOptionsParser.getResult();
    myOptionsParser.clear();

    List<GitLogStatusInfo> result = myPathsParser.getResult();
    myPathsParser.clear();

    myIsInBody = true;

    return new GitLogRecord(options, result, mySupportsRawBody);
  }

  public void clear() {
    myOptionsParser.clear();
    myPathsParser.clear();
    myIsInBody = true;
  }

  @NotNull
  public String getPretty() {
    return myPretty;
  }

  @NotNull
  private String makeFormatFromOptions(@NotNull GitLogOption[] options) {
    Function<GitLogOption, String> function = option -> "%" + option.getPlaceholder();
    return encodeForGit(myRecordStart) + StringUtil.join(options, function, encodeForGit(myItemsSeparator)) + encodeForGit(myRecordEnd);
  }

  @NotNull
  private static String encodeForGit(@NotNull String line) {
    StringBuilder encoded = new StringBuilder();
    line.chars().forEachOrdered(c -> encoded.append("%x").append(String.format("%02x", c)));
    return encoded.toString();
  }

  @NotNull
  private static String generateRandomSequence() {
    int length = ERROR_COUNT.get() % (MAX_SEPARATOR_LENGTH - RECORD_START.length());
    StringBuilder tail = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int randomIndex = ThreadLocalRandom.current().nextInt(0, CONTROL_CHARS.length);
      tail.append(CONTROL_CHARS[randomIndex]);
    }
    return tail.toString();
  }

  private static void throwGFE(@NotNull String message, @NotNull CharSequence line) {
    ERROR_COUNT.incrementAndGet();
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

  // --name-status or no flag
  enum NameStatus {
    /**
     * No flag.
     */
    NONE,
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

    private final String myPlaceholder;

    GitLogOption(String placeholder) {
      myPlaceholder = placeholder;
    }

    private String getPlaceholder() {
      return myPlaceholder;
    }
  }

  private class OptionsParser {
    @NotNull private final GitLogOption[] myOptions;
    @NotNull private final PartialResult myResult = new PartialResult();

    public OptionsParser(@NotNull GitLogOption[] options) {
      myOptions = options;
    }

    public boolean parseLine(@NotNull CharSequence line) {
      int offset = 0;

      if (myResult.isEmpty()) {
        if (!CharArrayUtil.regionMatches(line, offset, myRecordStart)) {
          return false;
        }
        offset += myRecordStart.length();
      }

      while (offset < line.length()) {
        if (atRecordEnd(line, offset)) {
          myResult.finishItem();
          if (myResult.getResult().size() != myOptions.length) {
            throwGFE("Parsed incorrect options " + myResult.getResult() + " for " +
                     Arrays.toString(myOptions), line);
          }
          return true;
        }

        if (CharArrayUtil.regionMatches(line, offset, myItemsSeparator)) {
          myResult.finishItem();
          offset += myItemsSeparator.length();
        }
        else {
          char c = line.charAt(offset);
          myResult.append(c);
          offset++;
        }
      }

      myResult.append('\n');

      return false;
    }

    private boolean atRecordEnd(@NotNull CharSequence line, int offset) {
      return (offset == line.length() - myRecordEnd.length() && CharArrayUtil.regionMatches(line, offset, myRecordEnd));
    }

    @NotNull
    public Map<GitLogOption, String> getResult() {
      return createOptions(myResult.getResult());
    }

    @NotNull
    private Map<GitLogOption, String> createOptions(@NotNull List<String> options) {
      Map<GitLogOption, String> optionsMap = new HashMap<>(options.size());
      for (int index = 0; index < options.size(); index++) {
        optionsMap.put(myOptions[index], options.get(index));
      }
      return optionsMap;
    }

    public void clear() {
      myResult.clear();
    }

    public boolean isEmpty() {
      return myResult.isEmpty();
    }
  }

  private static class PathsParser {
    @NotNull private final NameStatus myNameStatusOption;
    @NotNull private List<GitLogStatusInfo> myStatuses = ContainerUtil.newArrayList();

    public PathsParser(@NotNull NameStatus nameStatusOption) {
      myNameStatusOption = nameStatusOption;
    }

    public void parseLine(@NotNull CharSequence line) {
      if (line.length() == 0) return;

      List<String> match = parsePathsLine(line);

      if (match.isEmpty()) {
        // ignore
      }
      else {
        if (myNameStatusOption != NameStatus.STATUS) throwGFE("Status list not expected", line);

        if (match.size() == 2) {
          myStatuses.add(new GitLogStatusInfo(GitChangeType.fromString(match.get(0)), match.get(1), null));
        }
        else {
          myStatuses.add(new GitLogStatusInfo(GitChangeType.fromString(match.get(0)), match.get(1), match.get(2)));
        }
      }
    }

    @NotNull
    private static List<String> parsePathsLine(@NotNull CharSequence line) {
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

    private static boolean atLineEnd(@NotNull CharSequence line, int offset) {
      while (offset < line.length() && (line.charAt(offset) == '\t' || line.charAt(offset) == ' ')) offset++;
      if (offset == line.length() || (line.charAt(offset) == '\n' || line.charAt(offset) == '\r')) return true;
      return false;
    }

    @NotNull
    public List<GitLogStatusInfo> getResult() {
      return myStatuses;
    }

    public void clear() {
      myStatuses = ContainerUtil.newArrayList();
    }

    public boolean expectsPaths() {
      return myNameStatusOption == NameStatus.STATUS;
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
